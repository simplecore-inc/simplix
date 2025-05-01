package dev.simplecore.simplix.demo.web.event.controller;

import dev.simplecore.simplix.demo.web.event.dto.SampleMessage;
import dev.simplecore.simplix.demo.web.event.receiver.SampleMessageEventReceiver;
import dev.simplecore.simplix.event.gateway.SimpliXEventGateway;
import dev.simplecore.simplix.event.model.SimpliXMessageEvent;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/messages")
@Tag(name = "event.message", description = "실시간 이벤트 처리")
@RequiredArgsConstructor
public class MessageController {

    private final SimpliXEventGateway eventGateway;
    private final SampleMessageEventReceiver sampleMessageEvent;
    private static final String TOPIC = "message-events";

    @PostMapping(path = "/publish")
    public ResponseEntity<SampleMessage> publishMessage(@RequestBody SampleMessage sampleMessage) {
        sampleMessage.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        SimpliXMessageEvent messageEvent = new SimpliXMessageEvent(sampleMessage, TOPIC);
        eventGateway.sendEvent(messageEvent);
        
        return ResponseEntity.ok(sampleMessage);
    }

    @GetMapping(path = "/subscribe", produces = "text/event-stream")
    public SseEmitter subscribe() {
        return sampleMessageEvent.subscribe();
    }
} 