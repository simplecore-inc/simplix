package dev.simplecore.simplix.sync.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op implementation of {@link InstanceSyncBroadcaster} for local (single-instance) mode.
 *
 * <p>All operations are silently ignored. Used when distributed sync is disabled.
 */
public class NoOpInstanceSyncBroadcaster implements InstanceSyncBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(NoOpInstanceSyncBroadcaster.class);

    public NoOpInstanceSyncBroadcaster() {
        log.debug("NoOp instance sync broadcaster initialized (local mode)");
    }

    @Override
    public void broadcast(String channel, byte[] payload) {
        // No-op in local mode
    }

    @Override
    public void subscribe(String channel, InboundPayloadListener listener) {
        // No-op in local mode
    }
}
