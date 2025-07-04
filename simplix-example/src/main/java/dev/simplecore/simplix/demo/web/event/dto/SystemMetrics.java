package dev.simplecore.simplix.demo.web.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMetrics {
    private double cpuUsage;
    private long totalMemory;
    private long usedMemory;
    private double memoryUsage;
    private OffsetDateTime timestamp;
} 