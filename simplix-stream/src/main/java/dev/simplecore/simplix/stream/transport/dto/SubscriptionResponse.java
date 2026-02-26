package dev.simplecore.simplix.stream.transport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for subscription update operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {

    /**
     * Whether the operation was fully successful.
     */
    private boolean success;

    /**
     * List of successfully subscribed resources.
     */
    private List<SubscribedResource> subscribed;

    /**
     * List of resources that failed to subscribe.
     */
    private List<FailedSubscription> failed;

    /**
     * Current total subscription count for the session.
     */
    private int totalCount;

    /**
     * Successfully subscribed resource details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribedResource {
        private String resource;
        private Map<String, Object> params;
        private String subscriptionKey;
        private long intervalMs;
    }

    /**
     * Failed subscription details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedSubscription {
        private String resource;
        private Map<String, Object> params;
        private String reason;
    }
}
