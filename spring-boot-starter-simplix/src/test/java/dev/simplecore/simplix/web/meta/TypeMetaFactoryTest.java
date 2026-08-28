package dev.simplecore.simplix.web.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.annotation.FieldLabel;
import dev.simplecore.simplix.web.meta.model.FieldMeta;
import dev.simplecore.simplix.web.meta.model.TypeMeta;
import dev.simplecore.simplix.web.meta.model.TypeRef;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeMetaFactoryTest {

    static class CreateDTO {
        @NotBlank @Length(max = 100)
        @FieldLabel("{entities.Area.name}")
        @Schema(description = "Display name")
        private String name;

        @NotNull
        private Integer occupancyMax;

        @JsonIgnore
        private String internalNote;

        @JsonProperty("renamed")
        private String original;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getOccupancyMax() { return occupancyMax; }
        public void setOccupancyMax(Integer occupancyMax) { this.occupancyMax = occupancyMax; }
        public String getInternalNote() { return internalNote; }
        public void setInternalNote(String internalNote) { this.internalNote = internalNote; }
        public String getOriginal() { return original; }
        public void setOriginal(String original) { this.original = original; }
    }

    static class UpdateDTO extends CreateDTO {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    /** A setter-only property has no field and no getter — {@code getAccessor()} returns null. */
    static class WriteOnlyDTO {
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public void setHidden(String hidden) { /* stored elsewhere, never read back */ }
    }

    static class DetailDTO {
        private long version;             // primitive → required without any annotation

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String siteId;            // declared required by the server

        private String note;              // neither → optional

        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }
        public String getSiteId() { return siteId; }
        public void setSiteId(String siteId) { this.siteId = siteId; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    private final MetaTypeRegistry registry = new MetaTypeRegistry();
    private final TypeMetaFactory factory =
            new TypeMetaFactory(new ObjectMapper(), new TypeRefMapper(registry), registry);

    private FieldMeta field(TypeMeta meta, String name) {
        return meta.fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void ownFieldsOnlyAndExtendsChain() {
        TypeMeta update = factory.create(UpdateDTO.class);
        assertThat(update.extendsType()).isEqualTo("CreateDTO");
        assertThat(update.fields()).extracting(FieldMeta::name).containsExactly("id");
        assertThat(registry.pendingTypes()).contains(CreateDTO.class);
    }

    @Test
    void jacksonDecidesMembershipAndWireNames() {
        TypeMeta create = factory.create(CreateDTO.class);
        assertThat(create.fields()).extracting(FieldMeta::name)
            .containsExactlyInAnyOrder("name", "occupancyMax", "renamed");
    }

    @Test
    void labelsConstraintsAndDescriptions() {
        TypeMeta create = factory.create(CreateDTO.class);
        FieldMeta name = field(create, "name");
        assertThat(name.labelKey()).isEqualTo("entities.Area.name");
        assertThat(name.label()).isNull();
        assertThat(name.description()).isEqualTo("Display name");
        assertThat(name.required()).isTrue();
        assertThat(name.constraints()).extracting(c -> c.kind())
            .containsExactlyInAnyOrder("notBlank", "maxLength");
    }

    @Test
    void writeOnlyPropertyIsSkippedRatherThanThrown() {
        TypeMeta meta = factory.create(WriteOnlyDTO.class);
        assertThat(meta.fields()).extracting(FieldMeta::name).containsExactly("name");
    }

    @Test
    void requirednessComesFromDeclarationsOnly() {
        TypeMeta detail = factory.create(DetailDTO.class);
        assertThat(field(detail, "version").required()).isTrue();     // primitive
        assertThat(field(detail, "siteId").required()).isTrue();      // @Schema REQUIRED
        assertThat(field(detail, "note").required()).isFalse();       // nothing declared
        assertThat(field(detail, "version").type()).isEqualTo(TypeRef.number(true));
    }
}
