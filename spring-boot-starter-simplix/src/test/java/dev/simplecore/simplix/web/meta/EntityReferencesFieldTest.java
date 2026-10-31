package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.core.annotation.References;
import dev.simplecore.simplix.web.meta.model.ReferenceMeta;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void publishesTheIdentifierOtherwise() {
        assertThat(EntityReferences.resolve(Result.class, "voiceId")).isEqualTo(new ReferenceMeta("Voice", "voiceId", false));
    }
}
