package dev.simplecore.simplix.stream.infrastructure.distributed;

import dev.simplecore.simplix.stream.config.StreamProperties;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.KeyValueEntry;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * NATS JetStream KV-based per-subscription-key leader election.
 *
 * <p>Uses {@link KeyValue#create(String, byte[])} for first-write-wins
 * acquisition and bucket-level {@code maxAge} as the leadership TTL.
 * Periodic {@link KeyValue#update(String, byte[], long)} calls renew the
 * leadership by writing a fresh revision; if the renewal fails (revision
 * mismatch or connectivity), leadership is dropped and the registered
 * callback is invoked with {@code false}.
 */
@Slf4j
public class NatsLeaderElection implements LeaderElection {

    private static final String LEADER_KEY_PREFIX = "leader.";

    private final Connection connection;
    private final ScheduledExecutorService scheduler;
    private final String instanceId;
    private final byte[] instanceIdBytes;
    private final Duration leaderTtl;
    private final Duration renewInterval;
    private final String bucketName;

    private volatile KeyValue kv;

    private final Map<String, ScheduledFuture<?>> renewals = new ConcurrentHashMap<>();
    private final Map<String, Consumer<Boolean>> callbacks = new ConcurrentHashMap<>();
    private final Map<String, Long> revisions = new ConcurrentHashMap<>();

    public NatsLeaderElection(
            Connection connection,
            ScheduledExecutorService scheduler,
            String instanceId,
            StreamProperties properties) {
        this.connection = connection;
        this.scheduler = scheduler;
        this.instanceId = instanceId;
        this.instanceIdBytes = instanceId.getBytes(StandardCharsets.UTF_8);
        this.leaderTtl = properties.getDistributed().getLeaderElection().getTtl();
        this.renewInterval = properties.getDistributed().getLeaderElection().getRenewInterval();
        this.bucketName = properties.getDistributed().getNats().getLeaderBucket();
    }

    @Override
    public boolean tryBecomeLeader(String subscriptionKey, Consumer<Boolean> callback) {
        String key = sanitizeKey(LEADER_KEY_PREFIX + subscriptionKey);
        try {
            KeyValue store = resolveKv();
            try {
                long rev = store.create(key, instanceIdBytes);
                revisions.put(subscriptionKey, rev);
                if (callback != null) {
                    callbacks.put(subscriptionKey, callback);
                }
                startRenewal(subscriptionKey, key);
                log.info("Acquired leadership for: {} (instance: {}, revision: {})",
                        subscriptionKey, instanceId, rev);
                if (callback != null) {
                    callback.accept(true);
                }
                return true;
            } catch (JetStreamApiException e) {
                try {
                    KeyValueEntry entry = store.get(key);
                    if (entry != null && Arrays.equals(entry.getValue(), instanceIdBytes)) {
                        long rev = store.update(key, instanceIdBytes, entry.getRevision());
                        revisions.put(subscriptionKey, rev);
                        if (callback != null) {
                    callbacks.put(subscriptionKey, callback);
                }
                        startRenewal(subscriptionKey, key);
                        log.debug("Re-acquired leadership for: {} (revision: {})", subscriptionKey, rev);
                        if (callback != null) {
                            callback.accept(true);
                        }
                        return true;
                    }
                    String currentLeader = entry != null
                            ? new String(entry.getValue(), StandardCharsets.UTF_8) : "<unknown>";
                    log.debug("Failed to acquire leadership for {}: current leader is {}",
                            subscriptionKey, currentLeader);
                } catch (IOException | JetStreamApiException ex) {
                    log.debug("Leadership acquire-after-conflict failed: {}", ex.getMessage());
                }
                return false;
            }
        } catch (IOException e) {
            log.error("Error during NATS leader election for {}: {}",
                    subscriptionKey, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isLeader(String subscriptionKey) {
        String key = sanitizeKey(LEADER_KEY_PREFIX + subscriptionKey);
        try {
            KeyValue store = resolveKv();
            KeyValueEntry entry = store.get(key);
            return entry != null && Arrays.equals(entry.getValue(), instanceIdBytes);
        } catch (Exception e) {
            log.error("Error checking leadership for {}: {}", subscriptionKey, e.getMessage());
            return false;
        }
    }

    @Override
    public void releaseLeadership(String subscriptionKey) {
        String key = sanitizeKey(LEADER_KEY_PREFIX + subscriptionKey);
        ScheduledFuture<?> renewalTask = renewals.remove(subscriptionKey);
        if (renewalTask != null) {
            renewalTask.cancel(false);
        }
        Consumer<Boolean> callback = callbacks.remove(subscriptionKey);
        revisions.remove(subscriptionKey);

        try {
            KeyValue store = resolveKv();
            KeyValueEntry entry = store.get(key);
            if (entry != null && Arrays.equals(entry.getValue(), instanceIdBytes)) {
                store.delete(key);
                log.info("Released leadership for: {} (instance: {})", subscriptionKey, instanceId);
            }
        } catch (Exception e) {
            log.error("Error releasing leadership for {}: {}", subscriptionKey, e.getMessage());
        }

        if (callback != null) {
            callback.accept(false);
        }
    }

    @Override
    public void releaseAll() {
        log.info("Releasing all NATS leadership (instance: {})", instanceId);
        String[] keys = renewals.keySet().toArray(new String[0]);
        for (String key : keys) {
            releaseLeadership(key);
        }
    }

    @Override
    public String getLeader(String subscriptionKey) {
        String key = sanitizeKey(LEADER_KEY_PREFIX + subscriptionKey);
        try {
            KeyValue store = resolveKv();
            KeyValueEntry entry = store.get(key);
            return entry != null ? new String(entry.getValue(), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            log.error("Error reading leader for {}: {}", subscriptionKey, e.getMessage());
            return null;
        }
    }

    @Override
    public int getLeadershipCount() {
        return renewals.size();
    }

    private void startRenewal(String subscriptionKey, String kvKey) {
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> renew(subscriptionKey, kvKey),
                renewInterval.toMillis(),
                renewInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        renewals.put(subscriptionKey, task);
    }

    private void renew(String subscriptionKey, String kvKey) {
        Long lastRev = revisions.get(subscriptionKey);
        if (lastRev == null) {
            return;
        }
        try {
            KeyValue store = resolveKv();
            long rev = store.update(kvKey, instanceIdBytes, lastRev);
            revisions.put(subscriptionKey, rev);
            log.trace("Renewed leadership for: {} (revision: {})", subscriptionKey, rev);
        } catch (JetStreamApiException e) {
            // Revision mismatch or key gone — leadership genuinely lost.
            log.info("Lost leadership for {} (revision conflict): {}", subscriptionKey, e.getMessage());
            dropLeadership(subscriptionKey);
        } catch (IOException e) {
            // Transient connectivity issue — keep leadership and retry next cycle.
            log.warn("Transient NATS error renewing leadership for {} (will retry): {}",
                    subscriptionKey, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Unexpected error renewing leadership for {}: {}",
                    subscriptionKey, e.getMessage());
        }
    }

    private void dropLeadership(String subscriptionKey) {
        ScheduledFuture<?> task = renewals.remove(subscriptionKey);
        if (task != null) {
            task.cancel(false);
        }
        revisions.remove(subscriptionKey);
        Consumer<Boolean> cb = callbacks.remove(subscriptionKey);
        if (cb != null) {
            cb.accept(false);
        }
    }

    private KeyValue resolveKv() throws IOException {
        KeyValue current = kv;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (kv == null) {
                ensureBucket(bucketName, leaderTtl);
                kv = connection.keyValue(bucketName);
            }
            return kv;
        }
    }

    private void ensureBucket(String bucketName, Duration maxAge) throws IOException {
        try {
            KeyValueManagement mgmt = connection.keyValueManagement();
            KeyValueConfiguration config = KeyValueConfiguration.builder()
                    .name(bucketName)
                    .maxHistoryPerKey(1)
                    .ttl(maxAge)
                    .build();
            mgmt.create(config);
            log.info("Created NATS KV bucket [name='{}', ttl={}]", bucketName, maxAge);
        } catch (JetStreamApiException e) {
            log.debug("NATS KV bucket '{}' already exists or could not be created: {}",
                    bucketName, e.getMessage());
        }
    }

    /**
     * Subscription keys may contain characters that are not valid in NATS KV keys
     * ({@code .} and {@code -} are allowed; spaces and most punctuation are not).
     * Replace any disallowed character with {@code _}.
     */
    private static String sanitizeKey(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean valid = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '/' || c == '=';
            sb.append(valid ? c : '_');
        }
        return sb.toString();
    }
}
