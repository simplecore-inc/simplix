package dev.simplecore.simplix.demo.web.event.receiver;

import dev.simplecore.simplix.demo.web.event.dto.SampleMessage;
import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.service.SimpliXEventReceiver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Receiver for sample message events.
 * This receiver handles events on the "message-events" channel.
 */
@Slf4j
@Component
public class SampleMessageEventReceiver implements SimpliXEventReceiver<SampleMessage> {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private static final String SUPPORTED_CHANNEL = "message-events";

    @Override
    public void onEvent(String channelName, SimpliXMessageEvent event, SampleMessage messageEvent) {
        List<SseEmitter> deadEmitters = new ArrayList<>();

        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("sample-message")
                        .data(messageEvent));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        });

        emitters.removeAll(deadEmitters);
    }

    @Override
    public String[] getSupportedChannels() {
        return new String[]{SUPPORTED_CHANNEL};
    }

    /**
     * Creates a new SSE emitter for a client to subscribe to message events.
     *
     * @return A new SSE emitter
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        
        emitters.add(emitter);
        
        try {
            emitter.send(SseEmitter.event().name("INIT").data("connected"));
        } catch (IOException e) {
            emitter.complete();
            return subscribe();
        }
        
        return emitter;
    }
} 