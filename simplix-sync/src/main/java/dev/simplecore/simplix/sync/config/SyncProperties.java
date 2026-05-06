package dev.simplecore.simplix.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for the SimpliX Sync module.
 *
 * <p>Prefix: {@code simplix.sync}
 */
@ConfigurationProperties(prefix = "simplix.sync")
public class SyncProperties {

    /** Enable/disable the sync module. Default: true. */
    private boolean enabled = true;

    /** Sync mode: LOCAL or DISTRIBUTED. Default: LOCAL. */
    private Mode mode = Mode.LOCAL;

    /** Distributed mode settings. */
    private Distributed distributed = new Distributed();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Distributed getDistributed() {
        return distributed;
    }

    public void setDistributed(Distributed distributed) {
        this.distributed = distributed;
    }

    public enum Mode {
        LOCAL,
        DISTRIBUTED
    }

    public static class Distributed {
        /**
         * Backend used for distributed broadcasting.
         *
         * <p>Default: {@link Broker#REDIS} (historical behaviour). Set to
         * {@link Broker#NATS} to use a NATS core pub/sub backend; the application
         * must provide an {@code io.nats.client.Connection} bean (typically
         * supplied by simplix-messaging when {@code simplix.messaging.broker=nats}).
         */
        private Broker broker = Broker.REDIS;

        /**
         * Legacy toggle preserved for backward compatibility. Has no effect on
         * autoconfiguration — broker selection is driven by {@link #broker}.
         *
         * @deprecated Use {@code simplix.sync.distributed.broker=REDIS} instead.
         */
        @Deprecated
        private boolean redisEnabled = true;

        public Broker getBroker() {
            return broker;
        }

        public void setBroker(Broker broker) {
            this.broker = broker;
        }

        @Deprecated
        @DeprecatedConfigurationProperty(replacement = "simplix.sync.distributed.broker")
        public boolean isRedisEnabled() {
            return redisEnabled;
        }

        @Deprecated
        public void setRedisEnabled(boolean redisEnabled) {
            this.redisEnabled = redisEnabled;
        }
    }

    public enum Broker {
        REDIS,
        NATS
    }
}
