package dev.simplecore.simplix.auth.service.impl;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.service.TokenBlacklistService;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.KeyValueEntry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

/**
 * NATS JetStream KV-based implementation of {@link TokenBlacklistService}.
 *
 * <p>Stores revoked JTIs in a single KV bucket whose name is configurable via
 * {@code simplix.auth.token.blacklist-nats-bucket}. The bucket is created
 * idempotently with a {@code maxAge} of
 * {@code simplix.auth.token.blacklist-nats-max-retention} (default 7 days),
 * which must be at least as long as the longest token lifetime that may be
 * blacklisted — NATS KV applies a single retention per bucket and does not
 * honour per-key TTLs.
 *
 * <p>Activated when {@code simplix.auth.token.enable-blacklist=true} and
 * {@code simplix.auth.token.blacklist-store=NATS}, provided an
 * {@code io.nats.client.Connection} bean is available (typically supplied by
 * simplix-messaging when its broker is set to {@code nats}).
 *
 * <p>When NATS is unavailable, behaviour mirrors the Redis implementation and
 * is governed by {@link SimpliXAuthProperties.BlacklistFailureMode}.
 */
@Service
@ConditionalOnClass(name = "io.nats.client.Connection")
@ConditionalOnBean(Connection.class)
@ConditionalOnProperty(name = "simplix.auth.token.enable-blacklist", havingValue = "true")
@ConditionalOnExpression("'${simplix.auth.token.blacklist-store:AUTO}'.equalsIgnoreCase('NATS')")
public class NatsTokenBlacklistService implements TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(NatsTokenBlacklistService.class);
    private static final byte[] PRESENT = new byte[]{'1'};

    private final Connection connection;
    private final SimpliXAuthProperties properties;
    private volatile KeyValue kv;

    public NatsTokenBlacklistService(Connection connection, SimpliXAuthProperties properties) {
        this.connection = connection;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        String bucket = properties.getToken().getBlacklistNatsBucket();
        Duration maxAge = properties.getToken().getBlacklistNatsMaxRetention();
        try {
            ensureBucket(bucket, maxAge);
            this.kv = connection.keyValue(bucket);
            log.info("NATS token blacklist initialized [bucket='{}', maxAge={}]", bucket, maxAge);
        } catch (Exception e) {
            log.error("Failed to initialize NATS token blacklist [bucket='{}']: {}",
                    bucket, e.getMessage());
            // Defer rebinding to first access — see resolveKv()
        }
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        try {
            KeyValue store = resolveKv();
            store.put(jti, PRESENT);
        } catch (Exception e) {
            log.error("Failed to blacklist JTI '{}' in NATS KV: {}", jti, e.getMessage());
            handleFailure(e);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        try {
            KeyValue store = resolveKv();
            KeyValueEntry entry = store.get(jti);
            return entry != null && entry.getValue() != null;
        } catch (Exception e) {
            return handleFailureForRead(jti, e);
        }
    }

    private KeyValue resolveKv() throws IOException {
        KeyValue current = kv;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (kv == null) {
                String bucket = properties.getToken().getBlacklistNatsBucket();
                ensureBucket(bucket, properties.getToken().getBlacklistNatsMaxRetention());
                kv = connection.keyValue(bucket);
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

    private void handleFailure(Exception e) {
        SimpliXAuthProperties.BlacklistFailureMode mode =
                properties.getToken().getBlacklistFailureMode();
        if (mode == SimpliXAuthProperties.BlacklistFailureMode.FAIL_OPEN) {
            log.warn("NATS unavailable for blacklist write (FAIL_OPEN) — continuing: {}",
                    e.getMessage());
            return;
        }
        if (e instanceof RuntimeException re) {
            throw re;
        }
        throw new IllegalStateException("Failed to write to NATS token blacklist", e);
    }

    private boolean handleFailureForRead(String jti, Exception e) {
        SimpliXAuthProperties.BlacklistFailureMode mode =
                properties.getToken().getBlacklistFailureMode();
        if (mode == SimpliXAuthProperties.BlacklistFailureMode.FAIL_OPEN) {
            log.warn("NATS unavailable for blacklist check (FAIL_OPEN) — allowing token through. JTI: {}, error: {}",
                    jti, e.getMessage());
            return false;
        }
        log.error("NATS unavailable for blacklist check (FAIL_CLOSED) — denying token. JTI: {}, error: {}",
                jti, e.getMessage());
        if (e instanceof RuntimeException re) {
            throw re;
        }
        throw new IllegalStateException("Failed to read from NATS token blacklist", e);
    }
}
