package dev.simplecore.simplix.hibernate.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(WriteLockRetryAspectTest.Config.class)
@DisplayName("WriteLockRetryAspect")
class WriteLockRetryAspectTest {

    @Autowired
    private OwnTransactionService ownTransaction;

    @Autowired
    private JoiningCallerService joiningCaller;

    @Autowired
    private RequiresNewCallerService requiresNewCaller;

    @Autowired
    private JoiningParticipantService joiningParticipant;

    @Autowired
    private RequiresNewParticipantService requiresNewParticipant;

    @Autowired
    private AfterCommitNotifier afterCommitNotifier;

    @Autowired
    private ApplicationEventPublisher publisher;

    @BeforeEach
    void resetCounters() {
        ownTransaction.reset();
        joiningParticipant.reset();
        requiresNewParticipant.reset();
        requiresNewCaller.reset();
        afterCommitNotifier.reset();
    }

    @Test
    @DisplayName("starts a call that opens its own transaction over until it succeeds")
    void retriesACallThatOpensItsOwnTransaction() {
        ownTransaction.failTimes(2);

        assertThatCode(() -> ownTransaction.run()).doesNotThrowAnyException();
        assertThat(ownTransaction.callCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("gives up once the configured attempts are spent")
    void givesUpAfterTheConfiguredAttempts() {
        ownTransaction.failTimes(Integer.MAX_VALUE);

        assertThatThrownBy(() -> ownTransaction.run())
                .isInstanceOf(CannotAcquireLockException.class);
        assertThat(ownTransaction.callCount()).isEqualTo(ATTEMPTS);
    }

    @Test
    @DisplayName("leaves a version conflict to the caller without retrying")
    void doesNotRetryAVersionConflict() {
        ownTransaction.failTimes(2);
        ownTransaction.failWith(() -> new OptimisticLockingFailureException("two edits on one record"));

        assertThatThrownBy(() -> ownTransaction.run())
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(ownTransaction.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("does not retry a participant that joined the caller's transaction")
    void doesNotRetryAJoiningParticipant() {
        joiningParticipant.failTimes(Integer.MAX_VALUE);

        assertThatThrownBy(() -> joiningCaller.run())
                .isInstanceOf(CannotAcquireLockException.class);
        // The participant runs once per attempt of the transaction that owns it, never on its own.
        assertThat(joiningParticipant.callCount()).isEqualTo(ATTEMPTS);
    }

    @Test
    @DisplayName("retries a REQUIRES_NEW participant even though the caller's transaction is active")
    void retriesARequiresNewParticipant() {
        requiresNewParticipant.failTimes(2);

        assertThatCode(() -> requiresNewCaller.run()).doesNotThrowAnyException();
        assertThat(requiresNewParticipant.callCount()).isEqualTo(3);
        // Proves the three calls came from the participant's own retry, not from the caller's.
        assertThat(requiresNewCaller.callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("retries a REQUIRES_NEW listener running after the commit that triggered it")
    void retriesAnAfterCommitListener() {
        afterCommitNotifier.failTimes(2);

        assertThatCode(() -> ownTransaction.publish(publisher)).doesNotThrowAnyException();
        assertThat(afterCommitNotifier.callCount()).isEqualTo(3);
    }

    private static final int ATTEMPTS = 4;

    record Notification(String id) {
    }

    @Configuration
    @EnableAspectJAutoProxy
    @EnableTransactionManagement
    static class Config {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .generateUniqueName(true)
                    .build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        WriteLockRetryProperties writeLockRetryProperties() {
            WriteLockRetryProperties properties = new WriteLockRetryProperties();
            properties.setAttempts(ATTEMPTS);
            properties.setInitialBackoff(Duration.ofMillis(1));
            return properties;
        }

        @Bean
        WriteLockRetryAspect writeLockRetryAspect(WriteLockRetryProperties properties) {
            return new WriteLockRetryAspect(properties);
        }

        @Bean
        OwnTransactionService ownTransactionService() {
            return new OwnTransactionService();
        }

        @Bean
        JoiningCallerService joiningCallerService(JoiningParticipantService participant) {
            return new JoiningCallerService(participant);
        }

        @Bean
        JoiningParticipantService joiningParticipantService() {
            return new JoiningParticipantService();
        }

        @Bean
        RequiresNewCallerService requiresNewCallerService(RequiresNewParticipantService participant) {
            return new RequiresNewCallerService(participant);
        }

        @Bean
        RequiresNewParticipantService requiresNewParticipantService() {
            return new RequiresNewParticipantService();
        }

        @Bean
        AfterCommitNotifier afterCommitNotifier() {
            return new AfterCommitNotifier();
        }
    }

    /**
     * Counts its calls and throws a configured number of times before succeeding.
     *
     * <p>State is reached through methods rather than fields: these beans are handed out as
     * proxies, and a proxy carries its own uninitialized copy of every field.
     */
    static class FailingWork {

        private final AtomicInteger calls = new AtomicInteger();
        private int failures;
        private Supplier<RuntimeException> thrown =
                () -> new CannotAcquireLockException("the write lock stayed held");

        void reset() {
            calls.set(0);
            failures = 0;
            thrown = () -> new CannotAcquireLockException("the write lock stayed held");
        }

        int callCount() {
            return calls.get();
        }

        void failTimes(int times) {
            this.failures = times;
        }

        void failWith(Supplier<RuntimeException> thrown) {
            this.thrown = thrown;
        }

        void work() {
            if (calls.incrementAndGet() <= failures) {
                throw thrown.get();
            }
        }
    }

    static class OwnTransactionService extends FailingWork {

        @Transactional
        void run() {
            work();
        }

        @Transactional
        void publish(ApplicationEventPublisher publisher) {
            publisher.publishEvent(new Notification("n-1"));
        }
    }

    static class JoiningCallerService {

        private final JoiningParticipantService participant;

        JoiningCallerService(JoiningParticipantService participant) {
            this.participant = participant;
        }

        @Transactional
        void run() {
            participant.run();
        }
    }

    static class JoiningParticipantService extends FailingWork {

        @Transactional(propagation = Propagation.REQUIRED)
        void run() {
            work();
        }
    }

    /**
     * Swallows the participant's failure, the way a caller treats work it does not depend on.
     *
     * <p>This is what makes the participant the only thing that can retry: were the failure to
     * reach this transaction's own advice, retrying <em>it</em> would re-invoke the participant
     * and the call count could not tell the two apart.
     */
    static class RequiresNewCallerService {

        private final RequiresNewParticipantService participant;
        private final AtomicInteger calls = new AtomicInteger();

        RequiresNewCallerService(RequiresNewParticipantService participant) {
            this.participant = participant;
        }

        void reset() {
            calls.set(0);
        }

        int callCount() {
            return calls.get();
        }

        @Transactional
        void run() {
            calls.incrementAndGet();
            try {
                participant.run();
            } catch (CannotAcquireLockException e) {
                // The participant is independent: its failure does not condemn this transaction.
            }
        }
    }

    static class RequiresNewParticipantService extends FailingWork {

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        void run() {
            work();
        }
    }

    /** The shape a notifier takes: it reads its own data after the transaction it reacts to. */
    static class AfterCommitNotifier extends FailingWork {

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
        public void onNotification(Notification event) {
            work();
        }
    }
}
