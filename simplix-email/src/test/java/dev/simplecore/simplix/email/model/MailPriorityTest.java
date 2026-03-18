package dev.simplecore.simplix.email.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MailPriority")
class MailPriorityTest {

    @Test
    @DisplayName("LOW should have weight 1")
    void lowShouldHaveWeight1() {
        assertThat(MailPriority.LOW.getWeight()).isEqualTo(1);
    }

    @Test
    @DisplayName("NORMAL should have weight 3")
    void normalShouldHaveWeight3() {
        assertThat(MailPriority.NORMAL.getWeight()).isEqualTo(3);
    }

    @Test
    @DisplayName("HIGH should have weight 5")
    void highShouldHaveWeight5() {
        assertThat(MailPriority.HIGH.getWeight()).isEqualTo(5);
    }

    @Test
    @DisplayName("CRITICAL should have weight 10")
    void criticalShouldHaveWeight10() {
        assertThat(MailPriority.CRITICAL.getWeight()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should contain all expected values")
    void shouldContainAllValues() {
        assertThat(MailPriority.values())
                .containsExactly(MailPriority.LOW, MailPriority.NORMAL, MailPriority.HIGH, MailPriority.CRITICAL);
    }

    @Test
    @DisplayName("Should have increasing weights in order")
    void shouldHaveIncreasingWeights() {
        assertThat(MailPriority.LOW.getWeight()).isLessThan(MailPriority.NORMAL.getWeight());
        assertThat(MailPriority.NORMAL.getWeight()).isLessThan(MailPriority.HIGH.getWeight());
        assertThat(MailPriority.HIGH.getWeight()).isLessThan(MailPriority.CRITICAL.getWeight());
    }
}
