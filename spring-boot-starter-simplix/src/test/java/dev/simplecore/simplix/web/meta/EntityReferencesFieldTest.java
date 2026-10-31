package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.core.annotation.References;
import dev.simplecore.simplix.web.meta.model.ReferenceMeta;
import jakarta.persistence.Id;
import dev.simplecore.simplix.core.annotation.NameField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityReferencesFieldTest {

    static class Voice {
        @Id String voiceId;
        String voiceCode;
    }

    static class Result {
        @Id String resultId;
        @References(value = Voice.class, field = "voiceCode") String voiceCode;
        @References(Voice.class) String voiceId;
    }

    @Test
    void publishesTheFieldTheValueMatchesWhereTheAnnotationNamesOne() {
        assertThat(EntityReferences.resolve(Result.class, "voiceCode")).isEqualTo(new ReferenceMeta("Voice", "voiceCode", false));
    }

    static class Titled {
        @Id String titledId;
        String titledCode;
        @NameField String titledName;
    }

    static class Base {
        @NameField String baseName;
    }

    static class Inherited extends Base {
        @Id String inheritedId;
        String other;
    }

    static class Doubled extends Base {
        @Id String doubledId;
        @NameField String doubledName;
    }

    @Test
    void nameFieldIsTheMarkedFieldWhateverComesFirst() {
        assertThat(EntityReferences.nameFieldOf(Titled.class)).isEqualTo("titledName");
    }

    @Test
    void nameFieldIsFoundOnASuperclass() {
        assertThat(EntityReferences.nameFieldOf(Inherited.class)).isEqualTo("baseName");
    }

    @Test
    void nameFieldIsAbsentWhereNothingIsMarked() {
        assertThat(EntityReferences.nameFieldOf(Voice.class)).isNull();
    }

    @Test
    void twoMarksInOneHierarchyAreRefusedNamingBothFields() {
        assertThatThrownBy(() -> EntityReferences.nameFieldOf(Doubled.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("doubledName")
                .hasMessageContaining("baseName");
    }

    @Test
    void publishesTheIdentifierOtherwise() {
        assertThat(EntityReferences.resolve(Result.class, "voiceId")).isEqualTo(new ReferenceMeta("Voice", "voiceId", false));
    }
}
