package dev.simplecore.simplix.demo.web.event.service;

import dev.simplecore.simplix.demo.web.event.dto.SystemMetrics;
import dev.simplecore.simplix.event.gateway.SimpliXEventGateway;
import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMetricsService {

    private final SimpliXEventGateway eventGateway;
    private static final String METRICS_CHANNEL = "system-metrics";
    
    @Scheduled(fixedRate = 1000)
    public void collectAndPublishMetrics() {
        SystemMetrics metrics = collectSystemMetrics();
//        log.debug("Collected metrics: CPU={}%, Memory={}%",
//                String.format("%.2f", metrics.getCpuUsage()),
//                String.format("%.2f", metrics.getMemoryUsage()));
        
        SimpliXMessageEvent event = new SimpliXMessageEvent(metrics, METRICS_CHANNEL);
        eventGateway.sendEvent(event);
    }
    
    private SystemMetrics collectSystemMetrics() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        double cpuUsage = osBean.getSystemLoadAverage();
        if (cpuUsage < 0) { // 일부 시스템에서는 -1을 반환할 수 있음
            cpuUsage = ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad() * 100;
        } else {
            cpuUsage = cpuUsage * 100 / osBean.getAvailableProcessors();
        }
        
        long totalMemory = memoryBean.getHeapMemoryUsage().getMax() + memoryBean.getNonHeapMemoryUsage().getMax();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() + memoryBean.getNonHeapMemoryUsage().getUsed();
        double memoryUsage = (double) usedMemory / totalMemory * 100;
        
        return SystemMetrics.builder()
                .cpuUsage(cpuUsage)
                .totalMemory(totalMemory)
                .usedMemory(usedMemory)
                .memoryUsage(memoryUsage)
                .timestamp(OffsetDateTime.now())
                .build();
    }
} 