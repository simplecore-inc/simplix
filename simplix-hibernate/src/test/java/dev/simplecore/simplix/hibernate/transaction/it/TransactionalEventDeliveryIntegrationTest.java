package dev.simplecore.simplix.hibernate.transaction.it;

import dev.simplecore.simplix.core.event.model.EventMessage;
import dev.simplecore.simplix.hibernate.transaction.SimpliXJpaTransactionManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TransactionEventITApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:txit;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "simplix.hibernate.cache.disabled=true"
})
@DisplayName("R-1: real-commit BEFORE_COMMIT event delivery guarantee")
class TransactionalEventDeliveryIntegrationTest {

    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private RecordingEventConsumer consumer;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager em;

    @BeforeEach
    void reset() { consumer.reset(); }

    @Test // precondition: the auto-configured TM is ours
    @DisplayName("the auto-configured transaction manager is SimpliXJpaTransactionManager")
    void activeTransactionManagerIsSimpliX() {
        assertThat(transactionManager).isInstanceOf(SimpliXJpaTransactionManager.class);
    }

    @Test // R-1(a) hard delete, R-2 payload
    @DisplayName("R-1(a): flushless hard delete delivers exactly one DELETE event inside the transaction")
    void flushlessHardDelete_deliversExactlyOneDeleteEventInsideTransaction() {
        HardDeleteItem item = new HardDeleteItem("n", "owner-1");
        transactionTemplate.executeWithoutResult(tx -> em.persist(item));
        consumer.reset();

        transactionTemplate.executeWithoutResult(tx ->
                em.remove(em.find(HardDeleteItem.class, item.getId()))); // no flush

        List<EventMessage> deletes = consumer.beforeCommit.stream()
                .filter(e -> e.isType("HARD_ITEM_DELETED")).toList();
        assertThat(deletes).hasSize(1);
        assertThat(deletes.get(0).payload()).containsEntry("ownerId", "owner-1"); // pre-delete snapshot
        assertThat(consumer.beforeCommitTxActive).allMatch(Boolean::booleanValue); // delivered inside the tx
    }

    @Test // R-1(a) @SQLDelete soft delete - exactly one, no double publication
    @DisplayName("R-1(a): flushless soft delete delivers exactly one DELETE event (no double publication)")
    void flushlessSoftDelete_deliversExactlyOneDeleteEvent() {
        SoftDeleteItem item = new SoftDeleteItem("s");
        transactionTemplate.executeWithoutResult(tx -> em.persist(item));
        consumer.reset();

        transactionTemplate.executeWithoutResult(tx ->
                em.remove(em.find(SoftDeleteItem.class, item.getId())));

        assertThat(consumer.beforeCommit.stream().filter(e -> e.isType("SOFT_ITEM_DELETED"))).hasSize(1);
        Boolean deleted = (Boolean) em.createNativeQuery(
                "SELECT deleted FROM soft_delete_items WHERE id = ?")
                .setParameter(1, item.getId()).getSingleResult();
        assertThat(deleted).isTrue(); // row soft-deleted, not removed
    }

    @Test // R-1(b) dirty-check-only update - THE TM prover (red on stock JpaTransactionManager)
    @DisplayName("R-1(b): dirty-check-only update delivers an UPDATE event with changedProperties")
    void dirtyCheckOnlyUpdate_deliversUpdateEventWithChangedProperties() {
        HardDeleteItem item = new HardDeleteItem("before", "owner-2");
        transactionTemplate.executeWithoutResult(tx -> em.persist(item));
        consumer.reset();

        transactionTemplate.executeWithoutResult(tx ->
                em.find(HardDeleteItem.class, item.getId()).setName("after")); // no save, no flush

        assertThat(consumer.beforeCommit).anySatisfy(e -> {
            assertThat(e.eventType()).isEqualTo("HARD_ITEM_UPDATED");
            assertThat(e.changedProperties()).contains("name");
        });
    }

    @Test // R-1(c) rollback - nothing delivered at any phase
    @DisplayName("R-1(c): rollback delivers nothing at any phase")
    void rollback_deliversNothing() {
        HardDeleteItem item = new HardDeleteItem("r", "owner-3");
        transactionTemplate.executeWithoutResult(tx -> em.persist(item));
        consumer.reset();

        transactionTemplate.executeWithoutResult(tx -> {
            em.remove(em.find(HardDeleteItem.class, item.getId()));
            tx.setRollbackOnly();
        });

        assertThat(consumer.beforeCommit).isEmpty();
        assertThat(consumer.afterCommit).isEmpty();
        assertThat(em.find(HardDeleteItem.class, item.getId())).isNotNull(); // change rolled back
    }

    @Test // R-1(d) consumer exception aborts the commit
    @DisplayName("R-1(d): BEFORE_COMMIT consumer exception aborts the commit and the change")
    void beforeCommitConsumerException_abortsCommitAndChange() {
        HardDeleteItem item = new HardDeleteItem("x", "owner-4");
        transactionTemplate.executeWithoutResult(tx -> em.persist(item));
        consumer.reset();
        consumer.failOnDelete = true;

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx ->
                em.remove(em.find(HardDeleteItem.class, item.getId()))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(em.find(HardDeleteItem.class, item.getId())).isNotNull(); // delete not committed
        assertThat(consumer.afterCommit).isEmpty();
    }

    @Test // R-1(e) AFTER_COMMIT unchanged: delivered only after commit
    @DisplayName("R-1(e): AFTER_COMMIT consumer receives events only after the commit")
    void afterCommitConsumer_receivesAfterCommitOnly() {
        transactionTemplate.executeWithoutResult(tx -> {
            em.persist(new HardDeleteItem("a", "owner-5"));
            assertThat(consumer.afterCommit).isEmpty(); // not yet, inside the tx
        });
        assertThat(consumer.afterCommit)
                .extracting(EventMessage::eventType).contains("HARD_ITEM_CREATED");
    }

    @Test // R-1(f) failOnError=false: publication failure is swallowed, business change commits
    @DisplayName("R-1(f): failOnError=false swallows a payload failure and still commits the change")
    void bestEffortPayloadFailure_commitsWithoutEvent() {
        BestEffortPayloadItem item = new BestEffortPayloadItem("be");
        transactionTemplate.executeWithoutResult(tx -> em.persist(item));

        assertThat(em.find(BestEffortPayloadItem.class, item.getId())).isNotNull(); // change committed
        assertThat(consumer.beforeCommit.stream().filter(e -> e.isType("BEST_EFFORT_CREATED"))).isEmpty();
        assertThat(consumer.afterCommit.stream().filter(e -> e.isType("BEST_EFFORT_CREATED"))).isEmpty();
    }

    @Test // R-1(g) failOnError=true (default): publication failure aborts the commit
    @DisplayName("R-1(g): failOnError=true aborts the commit on a payload failure")
    void strictPayloadFailure_abortsCommit() {
        StrictPayloadItem item = new StrictPayloadItem("st");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx -> em.persist(item)))
                .isInstanceOf(RuntimeException.class);

        Long rows = transactionTemplate.execute(tx -> (Long) em.createQuery(
                "SELECT count(e) FROM StrictPayloadItem e").getSingleResult());
        assertThat(rows).isZero(); // change rolled back
        assertThat(consumer.afterCommit).isEmpty();
    }
}
