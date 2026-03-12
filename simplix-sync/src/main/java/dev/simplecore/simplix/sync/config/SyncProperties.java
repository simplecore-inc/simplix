package dev.simplecore.simplix.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        /** Whether Redis is enabled for distributed sync. Default: true. */
        private boolean redisEnabled = true;

        public boolean isRedisEnabled() {
            return redisEnabled;
        }

        public void setRedisEnabled(boolean redisEnabled) {
            this.redisEnabled = redisEnabled;
        }
    }
}
