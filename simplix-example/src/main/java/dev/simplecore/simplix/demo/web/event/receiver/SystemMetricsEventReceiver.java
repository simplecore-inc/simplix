package dev.simplecore.simplix.demo.web.event.receiver;

import dev.simplecore.simplix.demo.web.event.dto.SystemMetrics;
import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.service.SimpliXEventReceiver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.PostConstruct;

/**
 * Receiver for system metrics events.
 * This receiver handles events on the "system-metrics" channel and forwards them to SSE clients.
 */
@Slf4j
@Component
public class SystemMetricsEventReceiver implements SimpliXEventReceiver<SystemMetrics> {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private static final String SUPPORTED_CHANNEL = "system-metrics";

    @PostConstruct
    public void init() {
        log.info("SystemMetricsEventReceiver initialized for channel: {}", SUPPORTED_CHANNEL);
    }

    @Override
    public void onEvent(String channelName, SimpliXMessageEvent event, SystemMetrics metrics) {
        List<SseEmitter> deadEmitters = new ArrayList<>();

        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("system-metrics")
                        .data(metrics));
            } catch (IOException e) {
                log.error("Failed to send metrics to emitter", e);
                deadEmitters.add(emitter);
            }
        });

        if (!deadEmitters.isEmpty()) {
            log.debug("Removing {} dead emitters", deadEmitters.size());
            emitters.removeAll(deadEmitters);
        }
    }

    @Override
    public String[] getSupportedChannels() {
        return new String[]{SUPPORTED_CHANNEL};
    }

    /**
     * Creates a new SSE emitter for a client to subscribe to system metrics events.
     *
     * @return A new SSE emitter
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        emitter.onCompletion(() -> {
            log.debug("SSE emitter completed");
            emitters.remove(emitter);
        });
        
        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out");
            emitters.remove(emitter);
        });
        
        emitters.add(emitter);
        log.info("New SSE emitter subscribed. Total emitters: {}", emitters.size());
        
        try {
            emitter.send(SseEmitter.event().name("INIT").data("connected"));
        } catch (IOException e) {
            log.error("Failed to send initial event to emitter", e);
            emitter.complete();
            return subscribe();
        }
        
        return emitter;
    }
} 