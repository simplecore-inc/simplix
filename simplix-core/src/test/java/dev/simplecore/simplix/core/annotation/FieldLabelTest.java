package dev.simplecore.simplix.core.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class FieldLabelTest {

    static class Sample {
        @FieldLabel("{entities.Sample.name}")
        String name;

        @FieldLabel("Plain Label")
        String title;
    }

    @Test
    void isReadableAtRuntimeOnFields() throws Exception {
        Field name = Sample.class.getDeclaredField("name");
        FieldLabel label = name.getAnnotation(FieldLabel.class);
        assertThat(label).isNotNull();
        assertThat(label.value()).isEqualTo("{entities.Sample.name}");

        Field title = Sample.class.getDeclaredField("title");
        assertThat(title.getAnnotation(FieldLabel.class).value()).isEqualTo("Plain Label");
    }
}
