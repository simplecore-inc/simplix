package dev.simplecore.simplix.messaging.broker.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PayloadEncoding")
class PayloadEncodingTest {

    @Test
    @DisplayName("should have BASE64 and RAW values")
    void shouldHaveBothValues() {
        assertThat(PayloadEncoding.values()).hasSize(2);
        assertThat(PayloadEncoding.valueOf("BASE64")).isEqualTo(PayloadEncoding.BASE64);
        assertThat(PayloadEncoding.valueOf("RAW")).isEqualTo(PayloadEncoding.RAW);
    }

    @Test
    @DisplayName("should return correct name for each encoding")
    void shouldReturnCorrectNames() {
        assertThat(PayloadEncoding.BASE64.name()).isEqualTo("BASE64");
        assertThat(PayloadEncoding.RAW.name()).isEqualTo("RAW");
    }
}
