package dev.simplecore.simplix.stream.transport.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SubscriptionRequest DTO.
 */
@DisplayName("SubscriptionRequest")
class SubscriptionRequestTest {

    @Nested
    @DisplayName("builder()")
    class BuilderMethod {

        @Test
        @DisplayName("should build request with subscriptions")
        void shouldBuildRequestWithSubscriptions() {
            SubscriptionRequest.SubscriptionItem item = SubscriptionRequest.SubscriptionItem.builder()
                    .resource("stock-price")
                    .params(Map.of("symbol", "AAPL"))
                    .build();

            SubscriptionRequest request = SubscriptionRequest.builder()
                    .subscriptions(List.of(item))
                    .build();

            assertThat(request.getSubscriptions()).hasSize(1);
            assertThat(request.getSubscriptions().get(0).getResource()).isEqualTo("stock-price");
            assertThat(request.getSubscriptions().get(0).getParams()).containsEntry("symbol", "AAPL");
        }
    }

    @Nested
    @DisplayName("SubscriptionItem")
    class SubscriptionItemClass {

        @Test
        @DisplayName("should build item with resource and params")
        void shouldBuildItemWithResourceAndParams() {
            SubscriptionRequest.SubscriptionItem item = SubscriptionRequest.SubscriptionItem.builder()
                    .resource("forex")
                    .params(Map.of("pair", "EURUSD"))
                    .build();

            assertThat(item.getResource()).isEqualTo("forex");
            assertThat(item.getParams()).containsEntry("pair", "EURUSD");
        }

        @Test
        @DisplayName("should build item with null params")
        void shouldBuildItemWithNullParams() {
            SubscriptionRequest.SubscriptionItem item = SubscriptionRequest.SubscriptionItem.builder()
                    .resource("heartbeat")
                    .build();

            assertThat(item.getResource()).isEqualTo("heartbeat");
            assertThat(item.getParams()).isNull();
        }

        @Test
        @DisplayName("should support no-args constructor")
        void shouldSupportNoArgsConstructor() {
            SubscriptionRequest.SubscriptionItem item = new SubscriptionRequest.SubscriptionItem();

            assertThat(item.getResource()).isNull();
            assertThat(item.getParams()).isNull();
        }
    }

    @Nested
    @DisplayName("no-args constructor")
    class NoArgsConstructor {

        @Test
        @DisplayName("should create empty request")
        void shouldCreateEmptyRequest() {
            SubscriptionRequest request = new SubscriptionRequest();

            assertThat(request.getSubscriptions()).isNull();
        }
    }

    @Nested
    @DisplayName("multiple subscriptions")
    class MultipleSubscriptions {

        @Test
        @DisplayName("should support multiple subscription items")
        void shouldSupportMultipleItems() {
            List<SubscriptionRequest.SubscriptionItem> items = List.of(
                    SubscriptionRequest.SubscriptionItem.builder()
                            .resource("stock-price")
                            .params(Map.of("symbol", "AAPL"))
                            .build(),
                    SubscriptionRequest.SubscriptionItem.builder()
                            .resource("forex")
                            .params(Map.of("pair", "EURUSD"))
                            .build(),
                    SubscriptionRequest.SubscriptionItem.builder()
                            .resource("heartbeat")
                            .build()
            );

            SubscriptionRequest request = SubscriptionRequest.builder()
                    .subscriptions(items)
                    .build();

            assertThat(request.getSubscriptions()).hasSize(3);
        }
    }
}
