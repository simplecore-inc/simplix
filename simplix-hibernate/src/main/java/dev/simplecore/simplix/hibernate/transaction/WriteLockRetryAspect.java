package dev.simplecore.simplix.hibernate.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.exception.LockAcquisitionException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 * starts from the state the first one did. Only the outermost transaction is retried — an inner
 * participant shares the outer one's fate, and re-running it inside a transaction already marked
 * for rollback would repeat work that cannot commit.
 *
 * <h3>What is deliberately not retried</h3>
 * A version conflict. Two people edited one record, and the application has to let somebody
 * choose between the two versions; retrying would silently discard one of them. It travels as
 * the same Spring exception type as a refused lock, so the two are told apart by looking for the
 * engine's own lock failure in the cause chain rather than by the wrapper.
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
        // An inner participant shares the outer transaction's fate, so there is nothing here to
        // start over. The outermost call retries for all of them.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
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
