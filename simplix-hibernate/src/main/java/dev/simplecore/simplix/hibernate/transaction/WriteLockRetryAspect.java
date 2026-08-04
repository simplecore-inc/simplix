package dev.simplecore.simplix.hibernate.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.exception.LockAcquisitionException;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Starts a transaction over when the engine refused it the write lock.
 *
 * <h3>Why this exists</h3>
 * An engine that admits one writer at a time gives a transaction its view of the data at the
 * first read. A second connection writing before this one reaches its own write leaves it
 * holding a view it can no longer extend, and the refusal arrives at commit rather than at the
 * statement. Waiting does not help — the view is already stale — so the only way through is to
 * discard the transaction and take a fresh one. SQLite calls this {@code SQLITE_BUSY_SNAPSHOT};
 * a server that locks rows rather than the database reports the same condition as a lock
 * timeout. Both arrive here.
 *
 * <h3>Why retrying is safe</h3>
 * The refusal is itself a rollback: nothing the attempt did survives it, so the next attempt
 * starts from the state the first one did. Only a call that opens a transaction of its own is
 * retried: one that joins an existing transaction shares its fate, and re-running it inside a
 * transaction already marked for rollback would repeat work that cannot commit. A
 * {@link Propagation#REQUIRES_NEW} call opens its own, so it is retried even when it runs inside
 * another transaction. A {@link Propagation#NESTED} one is not — it runs on the connection whose
 * view went stale, and rolling back to a savepoint does not refresh that view, so only the
 * transaction owning the connection can start it over.
 *
 * <h3>What is deliberately not retried</h3>
 * A version conflict. Two people edited one record, and the application has to let somebody
 * choose between the two versions; retrying would silently discard one of them. It arrives as
 * {@link OptimisticLockingFailureException}, which shares only
 * {@link ConcurrencyFailureException} with a refused lock — broad enough to cover both, so the
 * cause chain is searched for the two lock-refusal types by name rather than for the branch they
 * have in common.
 *
 * <p>Ordered outside the transaction advisor so that a retry opens a new transaction rather than
 * re-entering the failed one.
 *
 * @see WriteLockRetryProperties
 */
@Slf4j
@Aspect
@Order(WriteLockRetryAspect.ORDER)
@RequiredArgsConstructor
public class WriteLockRetryAspect {

    /**
     * Outside the transaction advisor, which sits at {@link Ordered#LOWEST_PRECEDENCE} unless an
     * application moved it. Advice with a lower number wraps advice with a higher one.
     */
    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    private final WriteLockRetryProperties properties;

    /**
     * @param joinPoint the transactional call
     * @return whatever the call returned
     * @throws Throwable whatever the call threw, once retrying is done or ruled out
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)"
            + " || @within(org.springframework.transaction.annotation.Transactional)")
    public Object retryOnRefusedWriteLock(ProceedingJoinPoint joinPoint) throws Throwable {
        // A call that joins an existing transaction shares its fate, so there is nothing here to
        // start over. Whichever call owns that transaction retries for all of them.
        if (!opensItsOwnTransaction(joinPoint)) {
            return joinPoint.proceed();
        }

        long backoff = properties.getInitialBackoff().toMillis();
        for (int attempt = 1; ; attempt++) {
            try {
                return joinPoint.proceed();
            } catch (RuntimeException e) {
                if (!lockWasRefused(e)) {
                    throw e;
                }
                if (attempt >= properties.getAttempts()) {
                    log.warn("{} gave up after {} attempts: the write lock stayed held",
                            joinPoint.getSignature().toShortString(), attempt);
                    throw e;
                }
                log.debug("{} was refused the write lock, starting attempt {} over",
                        joinPoint.getSignature().toShortString(), attempt + 1);
                sleep(spread(backoff));
                backoff *= 2;
            }
        }
    }

    /**
     * Whether this call is the one that decides the transaction's outcome.
     *
     * <p>With no transaction running, the call is about to start one. With one already running,
     * only {@link Propagation#REQUIRES_NEW} sets the outer one aside and opens an independent
     * transaction whose rollback leaves the outer one untouched; every other propagation either
     * joins the outer transaction or runs on its connection.
     *
     * <p>The distinction is not visible through
     * {@link TransactionSynchronizationManager#isActualTransactionActive()} alone: an event
     * listener running after commit still sees the completed transaction's synchronization, and
     * a {@code REQUIRES_NEW} call made from inside a transaction sees the caller's.
     *
     * @param joinPoint the transactional call
     * @return whether a retry would open a fresh transaction rather than re-enter a doomed one
     */
    private static boolean opensItsOwnTransaction(ProceedingJoinPoint joinPoint) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return true;
        }
        Transactional attribute = findTransactional(joinPoint);
        return attribute != null && attribute.propagation() == Propagation.REQUIRES_NEW;
    }

    /**
     * Reads the annotation the pointcut matched on, method first and declaring class second,
     * which is the order Spring itself resolves a transaction attribute in.
     *
     * @param joinPoint the transactional call
     * @return the annotation governing this call, or {@code null} if it carries none
     */
    private static Transactional findTransactional(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return null;
        }
        Object target = joinPoint.getTarget();
        Class<?> targetClass = target != null
                ? AopProxyUtils.ultimateTargetClass(target)
                : signature.getDeclaringType();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass);
        Transactional onMethod = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(targetClass, Transactional.class);
    }

    /**
     * @param thrown what the attempt threw
     * @return whether the engine refused the write lock, as opposed to a version conflict or any
     *         other failure
     */
    private static boolean lockWasRefused(Throwable thrown) {
        for (Throwable cause = thrown; cause != null && cause != cause.getCause();
             cause = cause.getCause()) {
            if (cause instanceof LockAcquisitionException || cause instanceof CannotAcquireLockException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Spreads a wait over the upper half of its window.
     *
     * <p>Transactions refused together would otherwise wait the same length and collide again on
     * the same schedule, which turns one contended moment into several. Landing each of them
     * somewhere different in the window is what lets them through one at a time.
     *
     * @param millis the window to spread over
     * @return a wait between half and all of it
     */
    private static long spread(long millis) {
        return millis / 2 + (long) (ThreadLocalRandom.current().nextDouble() * (millis / 2 + 1));
    }

    /**
     * @param millis how long to hold off before starting over
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the write lock", e);
        }
    }
}
