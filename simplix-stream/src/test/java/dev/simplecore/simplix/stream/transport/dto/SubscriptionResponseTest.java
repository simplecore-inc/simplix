package dev.simplecore.simplix.stream.transport.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SubscriptionResponse DTO.
 */
@DisplayName("SubscriptionResponse")
class SubscriptionResponseTest {

    @Nested
    @DisplayName("builder()")
    class BuilderMethod {

        @Test
        @DisplayName("should build successful response")
        void shouldBuildSuccessfulResponse() {
            SubscriptionResponse.SubscribedResource subscribed = SubscriptionResponse.SubscribedResource.builder()
                    .resource("stock-price")
                    .params(Map.of("symbol", "AAPL"))
                    .subscriptionKey("stock-price:abc123")
                    .intervalMs(5000)
                    .build();

            SubscriptionResponse response = SubscriptionResponse.builder()
                    .success(true)
                    .subscribed(List.of(subscribed))
                    .failed(List.of())
                    .totalCount(1)
                    .build();

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getSubscribed()).hasSize(1);
            assertThat(response.getFailed()).isEmpty();
            assertThat(response.getTotalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should build response with failures")
        void shouldBuildResponseWithFailures() {
            SubscriptionResponse.FailedSubscription failed = SubscriptionResponse.FailedSubscription.builder()
                    .resource("secret-data")
                    .params(Map.of("id", "123"))
                    .reason("Access denied")
                    .build();

            SubscriptionResponse response = SubscriptionResponse.builder()
                    .success(false)
                    .subscribed(List.of())
                    .failed(List.of(failed))
                    .totalCount(0)
                    .build();

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getFailed()).hasSize(1);
            assertThat(response.getFailed().get(0).getReason()).isEqualTo("Access denied");
        }
    }

    @Nested
    @DisplayName("SubscribedResource")
    class SubscribedResourceClass {

        @Test
        @DisplayName("should store all fields")
        void shouldStoreAllFields() {
            SubscriptionResponse.SubscribedResource resource = SubscriptionResponse.SubscribedResource.builder()
                    .resource("stock")
                    .params(Map.of("symbol", "GOOG"))
                    .subscriptionKey("stock:def456")
                    .intervalMs(1000)
                    .build();

            assertThat(resource.getResource()).isEqualTo("stock");
            assertThat(resource.getParams()).containsEntry("symbol", "GOOG");
            assertThat(resource.getSubscriptionKey()).isEqualTo("stock:def456");
            assertThat(resource.getIntervalMs()).isEqualTo(1000);
        }

        @Test
        @DisplayName("should support no-args constructor")
        void shouldSupportNoArgsConstructor() {
            SubscriptionResponse.SubscribedResource resource = new SubscriptionResponse.SubscribedResource();

            assertThat(resource.getResource()).isNull();
            assertThat(resource.getIntervalMs()).isZero();
        }
    }

    @Nested
    @DisplayName("FailedSubscription")
    class FailedSubscriptionClass {

        @Test
        @DisplayName("should store all fields")
        void shouldStoreAllFields() {
            SubscriptionResponse.FailedSubscription failed = SubscriptionResponse.FailedSubscription.builder()
                    .resource("restricted")
                    .params(Map.of("id", "999"))
                    .reason("Resource not found")
                    .build();

            assertThat(failed.getResource()).isEqualTo("restricted");
            assertThat(failed.getParams()).containsEntry("id", "999");
            assertThat(failed.getReason()).isEqualTo("Resource not found");
        }

        @Test
        @DisplayName("should support no-args constructor")
        void shouldSupportNoArgsConstructor() {
            SubscriptionResponse.FailedSubscription failed = new SubscriptionResponse.FailedSubscription();

            assertThat(failed.getResource()).isNull();
            assertThat(failed.getReason()).isNull();
        }
    }
}
