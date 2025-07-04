package dev.simplecore.simplix.demo.web.event.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.simplecore.simplix.demo.web.event.receiver.SystemMetricsEventReceiver;

@RestController
@RequestMapping("/api/system")
@Tag(name = "event.system", description = "Real-time System Events")
@RequiredArgsConstructor
public class SystemMetricsController {

    private final SystemMetricsEventReceiver systemMetricsEventReceiver;

    @GetMapping(path = "/metrics/stream", produces = "text/event-stream")
    public SseEmitter subscribeToMetrics() {
        return systemMetricsEventReceiver.subscribe();
    }
} 