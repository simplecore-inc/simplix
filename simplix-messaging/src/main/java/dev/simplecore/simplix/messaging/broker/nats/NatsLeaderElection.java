package dev.simplecore.simplix.messaging.broker.nats;

import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueEntry;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * KV-based leader election for single-instance background tasks
 * (e.g., the scheduled message poller). Uses optimistic locking
 * via {@link KeyValue#create} for first-write-wins and
 * {@link KeyValue#update} for revision-based renewal.
 */
@Slf4j
public class NatsLeaderElection {

    private final KeyValue kv;
    private final String key;
    private final byte[] instanceIdBytes;
    private final String instanceId;
    private final AtomicBoolean leader = new AtomicBoolean(false);
    private final AtomicLong lastRevision = new AtomicLong(0L);

    public NatsLeaderElection(KeyValue kv, String key, String instanceId) {
        this.kv = kv;
        this.key = key;
        this.instanceId = instanceId;
        this.instanceIdBytes = instanceId.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Attempts to acquire leadership using first-write-wins semantics.
     * If the key already exists and is owned by this instance, re-acquires via update.
     *
     * @return true if this instance is now the leader, false otherwise
     */
    public boolean tryAcquireLeadership() {
        try {
            long rev = kv.create(key, instanceIdBytes);
            lastRevision.set(rev);
            leader.set(true);
            log.info("Acquired leadership [key='{}', instance='{}', revision={}]", key, instanceId, rev);
            return true;
        } catch (JetStreamApiException e) {
            // Key already exists — check if owned by this instance.
            try {
                KeyValueEntry entry = kv.get(key);
                if (entry != null && Arrays.equals(entry.getValue(), instanceIdBytes)) {
                    long rev = kv.update(key, instanceIdBytes, entry.getRevision());
                    lastRevision.set(rev);
                    leader.set(true);
                    log.debug("Re-acquired leadership [key='{}', revision={}]", key, rev);
                    return true;
                }
                leader.set(false);
                return false;
            } catch (IOException | JetStreamApiException ex) {
                log.debug("Leadership acquire-after-conflict failed: {}", ex.getMessage());
                leader.set(false);
                return false;
            }
        } catch (IOException e) {
            log.warn("Leadership acquire failed [key='{}']: {}", key, e.getMessage());
            leader.set(false);
            return false;
        }
    }

    /**
     * Returns true if this instance currently holds leadership.
     */
    public boolean isLeader() {
        return leader.get();
    }

    /**
     * Renews leadership by updating the KV entry with the last known revision.
     * If the update fails (revision mismatch or connectivity), leadership is dropped.
     *
     * @return true if renewal succeeded, false if leadership was lost
     */
    public boolean renew() {
        if (!leader.get()) return false;
        try {
            long rev = kv.update(key, instanceIdBytes, lastRevision.get());
            lastRevision.set(rev);
            return true;
        } catch (JetStreamApiException | IOException e) {
            log.info("Leadership renew failed, dropping leadership [key='{}']: {}", key, e.getMessage());
            leader.set(false);
            return false;
        }
    }

    /**
     * Releases leadership by deleting the KV entry, but only if this instance still owns it.
     * No-op if this instance is not the leader.
     */
    public void releaseLeadership() {
        if (!leader.compareAndSet(true, false)) return;
        try {
            KeyValueEntry entry = kv.get(key);
            if (entry == null) {
                log.debug("Leadership entry already gone [key='{}']", key);
                return;
            }
            if (!Arrays.equals(entry.getValue(), instanceIdBytes)) {
                log.info("Leadership held by different instance, not deleting [key='{}', other='{}']",
                        key, new String(entry.getValue(), StandardCharsets.UTF_8));
                return;
            }
            kv.delete(key);
            log.info("Released leadership [key='{}']", key);
        } catch (Exception e) {
            log.debug("Leadership release error: {}", e.getMessage());
        }
    }

    /**
     * Returns the instance ID used to identify this node in the KV store.
     */
    public String getInstanceId() {
        return instanceId;
    }
}
