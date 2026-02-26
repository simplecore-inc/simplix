package dev.simplecore.simplix.stream.transport.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for updating subscriptions.
 * <p>
 * Contains a list of resources to subscribe to.
 * The system will calculate the diff with current subscriptions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionRequest {

    /**
     * List of resources to subscribe to.
     */
    @NotNull(message = "Subscriptions list is required")
    @Size(max = 100, message = "Maximum 100 subscriptions allowed per request")
    @Valid
    private List<SubscriptionItem> subscriptions;

    /**
     * Individual subscription item.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionItem {

        /**
         * The resource name to subscribe to.
         */
        @NotNull(message = "Resource is required")
        @Size(min = 1, max = 100, message = "Resource must be between 1 and 100 characters")
        private String resource;

        /**
         * Optional parameters for the resource.
         */
        private Map<String, Object> params;
    }
}
