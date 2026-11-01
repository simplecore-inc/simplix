package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.core.annotation.References;
import dev.simplecore.simplix.web.meta.model.ReferenceMeta;
import jakarta.persistence.Id;
import dev.simplecore.simplix.core.annotation.NameField;
import dev.simplecore.simplix.core.annotation.NameFields;
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

    static class Stamped {
        String createdAt;
    }

    @NameFields({"jobKind", "createdAt"})
    static class Listed extends Stamped {
        @Id String listedId;
        String jobKind;
    }

    @NameFields({"jobKind", "missing"})
    static class ListedMissing extends Stamped {
        String jobKind;
    }

    @NameFields({"jobKind"})
    static class ListedAndMarked {
        String jobKind;
        @NameField String other;
    }

    static class Composite {
        @Id String compositeId;
        @NameField(order = 1) String compositeVersion;
        @NameField String compositeName;
    }

    static class Extended extends Base {
        @Id String extendedId;
        @NameField(order = 1) String extendedPart;
    }

    @Test
    void nameFieldIsTheMarkedFieldWhateverComesFirst() {
        assertThat(EntityReferences.nameFieldsOf(Titled.class)).containsExactly("titledName");
    }

    @Test
    void nameFieldIsFoundOnASuperclass() {
        assertThat(EntityReferences.nameFieldsOf(Inherited.class)).containsExactly("baseName");
    }

    @Test
    void nameFieldIsAbsentWhereNothingIsMarked() {
        assertThat(EntityReferences.nameFieldsOf(Voice.class)).isNull();
    }

    @Test
    void nameFieldsFollowTheirOrderNotTheirDeclaration() {
        assertThat(EntityReferences.nameFieldsOf(Composite.class)).containsExactly("compositeName", "compositeVersion");
    }

    @Test
    void nameFieldsJoinASuperclassMarkInOrder() {
        assertThat(EntityReferences.nameFieldsOf(Extended.class)).containsExactly("baseName", "extendedPart");
    }

    @Test
    void nameFieldsListedOnTheEntityTakeAnInheritedFieldInOrder() {
        assertThat(EntityReferences.nameFieldsOf(Listed.class)).containsExactly("jobKind", "createdAt");
    }

    @Test
    void aListedFieldTheHierarchyDoesNotDeclareIsRefused() {
        assertThatThrownBy(() -> EntityReferences.nameFieldsOf(ListedMissing.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void aListBesideMarksIsRefused() {
        assertThatThrownBy(() -> EntityReferences.nameFieldsOf(ListedAndMarked.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("other");
    }

    @Test
    void twoMarksWithOneOrderAreRefusedNamingBothFields() {
        assertThatThrownBy(() -> EntityReferences.nameFieldsOf(Doubled.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("doubledName")
                .hasMessageContaining("baseName");
    }

    @Test
    void publishesTheIdentifierOtherwise() {
        assertThat(EntityReferences.resolve(Result.class, "voiceId")).isEqualTo(new ReferenceMeta("Voice", "voiceId", false));
    }
}
