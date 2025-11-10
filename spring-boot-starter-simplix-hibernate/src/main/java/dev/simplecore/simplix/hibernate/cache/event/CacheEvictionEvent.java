package dev.simplecore.simplix.hibernate.cache.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Event published when cache eviction occurs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheEvictionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String entityClass;
    private String entityId;
    private String region;
    private String operation;
    private Long timestamp;
    private String nodeId;
}