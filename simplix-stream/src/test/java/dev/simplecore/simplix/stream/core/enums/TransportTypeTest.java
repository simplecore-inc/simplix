package dev.simplecore.simplix.stream.core.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TransportType enum.
 */
@DisplayName("TransportType")
class TransportTypeTest {

    @Test
    @DisplayName("should have SSE and WEBSOCKET values")
    void shouldHaveSseAndWebSocketValues() {
        TransportType[] values = TransportType.values();

        assertThat(values).hasSize(2);
        assertThat(values).containsExactly(TransportType.SSE, TransportType.WEBSOCKET);
    }

    @Test
    @DisplayName("should resolve SSE from name")
    void shouldResolveSseFromName() {
        TransportType type = TransportType.valueOf("SSE");

        assertThat(type).isEqualTo(TransportType.SSE);
    }

    @Test
    @DisplayName("should resolve WEBSOCKET from name")
    void shouldResolveWebSocketFromName() {
        TransportType type = TransportType.valueOf("WEBSOCKET");

        assertThat(type).isEqualTo(TransportType.WEBSOCKET);
    }

    @Test
    @DisplayName("should have correct ordinal values")
    void shouldHaveCorrectOrdinalValues() {
        assertThat(TransportType.SSE.ordinal()).isZero();
        assertThat(TransportType.WEBSOCKET.ordinal()).isEqualTo(1);
    }
}
