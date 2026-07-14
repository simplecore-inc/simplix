package dev.simplecore.simplix.hibernate.transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link JpaTransactionManager} that flushes the transactional {@link EntityManager}
 * in {@link #prepareForCommit} - the only hook that runs BEFORE the BEFORE_COMMIT
 * synchronization pass ({@code triggerBeforeCommit}) in spring-tx.
 * <p>
 * Stock behavior flushes inside {@code doCommit}, AFTER the BEFORE_COMMIT
 * synchronization snapshot has been iterated. JPA lifecycle events fired by that late
 * flush (deletes, dirty-check-only updates, saveAll batches of services that never
 * call {@code flush()}) register their
 * {@code @TransactionalEventListener(BEFORE_COMMIT)} synchronizations too late and
 * are silently dropped. Flushing here guarantees that every pending lifecycle event
 * is published - and its listener executed within the same transaction - before commit.
 */
public class SimpliXJpaTransactionManager extends JpaTransactionManager {

    public SimpliXJpaTransactionManager() {
        super();
    }

    public SimpliXJpaTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void prepareForCommit(DefaultTransactionStatus status) {
        // Flush only at the outermost commit of a read-write transaction. Participating
        // inner commits and read-only transactions (FlushMode.MANUAL) are skipped.
        if (status.isNewTransaction() && !status.isReadOnly()) {
            EntityManagerHolder emHolder = (EntityManagerHolder)
                    TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
            if (emHolder != null) {
                EntityManager em = emHolder.getEntityManager();
                if (em.isOpen() && em.isJoinedToTransaction()) {
                    em.flush();
                }
            }
        }
        super.prepareForCommit(status);
    }
}
