package dev.simplecore.simplix.hibernate.transaction.it;

import dev.simplecore.simplix.core.event.model.EventMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class RecordingEventConsumer {

    final List<EventMessage> beforeCommit = new CopyOnWriteArrayList<>();
    final List<Boolean> beforeCommitTxActive = new CopyOnWriteArrayList<>();
    final List<EventMessage> afterCommit = new CopyOnWriteArrayList<>();
    volatile boolean failOnDelete = false;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBeforeCommit(EventMessage event) {
        beforeCommit.add(event);
        beforeCommitTxActive.add(TransactionSynchronizationManager.isActualTransactionActive());
        if (failOnDelete && event.eventType().endsWith("_DELETED")) {
            throw new IllegalStateException("before-commit consumer failure (test)");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAfterCommit(EventMessage event) {
        afterCommit.add(event);
    }

    public void reset() {
        beforeCommit.clear();
        beforeCommitTxActive.clear();
        afterCommit.clear();
        failOnDelete = false;
    }
}
