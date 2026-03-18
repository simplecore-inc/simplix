package dev.simplecore.simplix.scheduler.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.*;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulerName - Custom scheduler name annotation")
class SchedulerNameTest {

    @Test
    @DisplayName("Should be retained at runtime")
    void shouldBeRetainedAtRuntime() {
        Retention retention = SchedulerName.class.getAnnotation(Retention.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("Should target methods only")
    void shouldTargetMethodsOnly() {
        Target target = SchedulerName.class.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    @Test
    @DisplayName("Should be documented")
    void shouldBeDocumented() {
        Documented documented = SchedulerName.class.getAnnotation(Documented.class);

        assertThat(documented).isNotNull();
    }

    @Test
    @DisplayName("Should have value attribute")
    void shouldHaveValueAttribute() throws NoSuchMethodException {
        Method valueMethod = SchedulerName.class.getDeclaredMethod("value");

        assertThat(valueMethod).isNotNull();
        assertThat(valueMethod.getReturnType()).isEqualTo(String.class);
    }

    @Test
    @DisplayName("Should be readable from annotated method")
    void shouldBeReadableFromAnnotatedMethod() throws NoSuchMethodException {
        Method method = AnnotatedSample.class.getDeclaredMethod("myScheduledMethod");
        SchedulerName annotation = method.getAnnotation(SchedulerName.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("custom-scheduler-name");
    }

    @Test
    @DisplayName("Should return null for non-annotated method")
    void shouldReturnNullForNonAnnotatedMethod() throws NoSuchMethodException {
        Method method = AnnotatedSample.class.getDeclaredMethod("regularMethod");
        SchedulerName annotation = method.getAnnotation(SchedulerName.class);

        assertThat(annotation).isNull();
    }

    // Sample class for annotation testing
    private static class AnnotatedSample {

        @SchedulerName("custom-scheduler-name")
        public void myScheduledMethod() {
        }

        public void regularMethod() {
        }
    }
}
