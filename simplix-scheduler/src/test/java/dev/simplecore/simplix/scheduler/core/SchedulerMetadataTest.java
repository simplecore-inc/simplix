package dev.simplecore.simplix.scheduler.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulerMetadata - Immutable metadata value object")
class SchedulerMetadataTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("Should build metadata with all fields")
        void shouldBuildWithAllFields() {
            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("TestScheduler_run")
                .className("com.example.TestScheduler")
                .methodName("run")
                .cronExpression("cron: 0 0 * * * ?")
                .shedlockName("test-lock")
                .schedulerType(SchedulerMetadata.SchedulerType.DISTRIBUTED)
                .build();

            assertThat(metadata.getSchedulerName()).isEqualTo("TestScheduler_run");
            assertThat(metadata.getClassName()).isEqualTo("com.example.TestScheduler");
            assertThat(metadata.getMethodName()).isEqualTo("run");
            assertThat(metadata.getCronExpression()).isEqualTo("cron: 0 0 * * * ?");
            assertThat(metadata.getShedlockName()).isEqualTo("test-lock");
            assertThat(metadata.getSchedulerType()).isEqualTo(SchedulerMetadata.SchedulerType.DISTRIBUTED);
        }

        @Test
        @DisplayName("Should build metadata with minimal fields")
        void shouldBuildWithMinimalFields() {
            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("SimpleJob_execute")
                .build();

            assertThat(metadata.getSchedulerName()).isEqualTo("SimpleJob_execute");
            assertThat(metadata.getClassName()).isNull();
            assertThat(metadata.getMethodName()).isNull();
            assertThat(metadata.getCronExpression()).isNull();
            assertThat(metadata.getShedlockName()).isNull();
            assertThat(metadata.getSchedulerType()).isNull();
        }

        @Test
        @DisplayName("Should build local scheduler metadata without shedlock name")
        void shouldBuildLocalSchedulerMetadata() {
            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("LocalJob_execute")
                .className("com.example.LocalJob")
                .methodName("execute")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .shedlockName(null)
                .build();

            assertThat(metadata.getSchedulerType()).isEqualTo(SchedulerMetadata.SchedulerType.LOCAL);
            assertThat(metadata.getShedlockName()).isNull();
        }
    }

    @Nested
    @DisplayName("SchedulerType enum")
    class SchedulerTypeTest {

        @Test
        @DisplayName("Should have LOCAL and DISTRIBUTED values")
        void shouldHaveTwoValues() {
            SchedulerMetadata.SchedulerType[] values = SchedulerMetadata.SchedulerType.values();
            assertThat(values).hasSize(2);
            assertThat(values).containsExactly(
                SchedulerMetadata.SchedulerType.LOCAL,
                SchedulerMetadata.SchedulerType.DISTRIBUTED
            );
        }

        @Test
        @DisplayName("Should resolve valueOf for LOCAL")
        void shouldResolveLocal() {
            assertThat(SchedulerMetadata.SchedulerType.valueOf("LOCAL"))
                .isEqualTo(SchedulerMetadata.SchedulerType.LOCAL);
        }

        @Test
        @DisplayName("Should resolve valueOf for DISTRIBUTED")
        void shouldResolveDistributed() {
            assertThat(SchedulerMetadata.SchedulerType.valueOf("DISTRIBUTED"))
                .isEqualTo(SchedulerMetadata.SchedulerType.DISTRIBUTED);
        }
    }

    @Nested
    @DisplayName("Equality")
    class EqualityTest {

        @Test
        @DisplayName("Two metadata objects with same fields should be equal")
        void shouldBeEqualWithSameFields() {
            SchedulerMetadata m1 = SchedulerMetadata.builder()
                .schedulerName("test")
                .className("com.example.Test")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            SchedulerMetadata m2 = SchedulerMetadata.builder()
                .schedulerName("test")
                .className("com.example.Test")
                .methodName("run")
                .schedulerType(SchedulerMetadata.SchedulerType.LOCAL)
                .build();

            assertThat(m1).isEqualTo(m2);
            assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
        }

        @Test
        @DisplayName("Two metadata objects with different fields should not be equal")
        void shouldNotBeEqualWithDifferentFields() {
            SchedulerMetadata m1 = SchedulerMetadata.builder()
                .schedulerName("test1")
                .build();

            SchedulerMetadata m2 = SchedulerMetadata.builder()
                .schedulerName("test2")
                .build();

            assertThat(m1).isNotEqualTo(m2);
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTest {

        @Test
        @DisplayName("Should return consistent values on repeated access")
        void shouldReturnConsistentValues() {
            SchedulerMetadata metadata = SchedulerMetadata.builder()
                .schedulerName("ImmutableTest")
                .className("com.example.Test")
                .build();

            assertThat(metadata.getSchedulerName()).isEqualTo("ImmutableTest");
            assertThat(metadata.getSchedulerName()).isEqualTo("ImmutableTest");
            assertThat(metadata.getClassName()).isEqualTo("com.example.Test");
            assertThat(metadata.getClassName()).isEqualTo("com.example.Test");
        }
    }
}
