package dev.simplecore.simplix.event.provider;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.core.event.EventPublisher;
import dev.simplecore.simplix.event.publisher.UnifiedEventPublisher;
import dev.simplecore.simplix.event.core.PublishOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * Implementation of Core EventPublisher that bridges to UnifiedEventPublisher
 * This is discovered via SPI by the core module
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnClass(name = "dev.simplecore.simplix.core.event.EventPublisher")
public class CoreEventPublisherImpl implements EventPublisher {

    private final UnifiedEventPublisher unifiedEventPublisher;

    @Override
    public void publish(Event event) {
        if (unifiedEventPublisher != null) {
            // Delegate to UnifiedEventPublisher with default options
            unifiedEventPublisher.publish(event, PublishOptions.defaults());
        } else {
            log.trace("UnifiedEventPublisher not available, event not published: {} for aggregate {}",
                event.getEventType(), event.getAggregateId());
        }
    }

    @Override
    public void publish(Event event, Object options) {
        if (unifiedEventPublisher != null) {
            unifiedEventPublisher.publish(event, options);
        }
    }

    @Override
    public boolean isAvailable() {
        return unifiedEventPublisher != null && unifiedEventPublisher.isAvailable();
    }

    @Override
    public String getName() {
        return "EventModule-Publisher";
    }

    @Override
    public int getPriority() {
        return 100; // High priority to be selected over other publishers
    }
}