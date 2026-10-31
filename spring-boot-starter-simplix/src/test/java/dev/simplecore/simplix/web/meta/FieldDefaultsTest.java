package dev.simplecore.simplix.web.meta;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldDefaultsTest {

    enum Tier { FREE, PREMIUM }

    /** The entity a DTO maps to, its defaults written the way Lombok's {@code @Builder.Default} emits them. */
    static class Voice {
        private Boolean active;
        private Integer sortOrder;
        private Tier tier = Tier.FREE;
        private String note;

        protected Voice() {}

        @SuppressWarnings("unused")
        private static Boolean $default$active() { return true; }

        @SuppressWarnings("unused")
        private static Integer $default$sortOrder() { return 0; }
    }

    static class CreateDTO {
        private Boolean active;
        private Integer sortOrder = 5;
        private Tier tier;
        private String note;
        private Object payload = new Object();
    }

    @Test
    void readsTheEntityBuilderDefaultWhereTheDtoStatesNone() {
        assertThat(FieldDefaults.of(CreateDTO.class, "active", Voice.class, "active")).isEqualTo(true);
    }

    @Test
    void prefersTheDtoInitializerOverTheEntity() {
        assertThat(FieldDefaults.of(CreateDTO.class, "sortOrder", Voice.class, "sortOrder")).isEqualTo(5);
    }

    @Test
    void publishesAnEnumDefaultByName() {
        assertThat(FieldDefaults.of(CreateDTO.class, "tier", Voice.class, "tier")).isEqualTo("FREE");
    }

    @Test
    void yieldsNothingForAFieldWithNoInitialValue() {
        assertThat(FieldDefaults.of(CreateDTO.class, "note", Voice.class, "note")).isNull();
    }

    @Test
    void yieldsNothingForAValueThatIsNotAScalar() {
        assertThat(FieldDefaults.of(CreateDTO.class, "payload", null, null)).isNull();
    }

    @Test
    void doesNotReachThroughADottedPath() {
        assertThat(FieldDefaults.of(CreateDTO.class, "active", Voice.class, "owner.active")).isNull();
    }
}
