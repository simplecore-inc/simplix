package dev.simplecore.simplix.web.mapping;

import dev.simplecore.simplix.core.annotation.VerbOnly;
import dev.simplecore.simplix.springboot.autoconfigure.SimpliXModelMapperAutoConfiguration;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration.AccessLevel;
import org.modelmapper.convention.MatchingStrategies;

import static org.assertj.core.api.Assertions.assertThat;

class VerbOnlyWriteSkipConditionTest {

    /** The mapper settings the SimpliX auto-configuration applies, without its converters. */
    private static ModelMapper plainMapper() {
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(AccessLevel.PRIVATE);
        return mapper;
    }

    private static ModelMapper guardedMapper() {
        ModelMapper mapper = plainMapper();
        CreateRequestNullSkipCondition.install(mapper);
        VerbOnlyWriteSkipCondition.install(mapper);
        return mapper;
    }

    private static ConnectionRequest request(boolean enabled, String name) {
        ConnectionRequest request = new ConnectionRequest();
        request.enabled = enabled;
        request.name = name;
        return request;
    }

    @Test
    @DisplayName("an update request that carries a verb-only field keeps the stored value and writes the rest")
    void update_keeps_the_stored_verb_only_value() {
        Connection stored = new Connection();
        stored.enabled = false;
        stored.name = "old";

        guardedMapper().map(request(true, "new"), stored);

        assertThat(stored.enabled).isFalse();
        assertThat(stored.name).isEqualTo("new");
    }

    @Test
    @DisplayName("a create request that sends the opposite value still starts at the entity default")
    void create_starts_at_the_entity_default() {
        Connection created = new Connection();
        ConnectionCreateDTO request = new ConnectionCreateDTO();
        request.enabled = false;
        request.name = "first";

        guardedMapper().map(request, created);

        assertThat(created.enabled).isTrue();
        assertThat(created.name).isEqualTo("first");
    }

    @Test
    @DisplayName("an entity written through a Lombok-style setter is guarded the same way as a bare field")
    void setter_destination_is_guarded() {
        SetterConnection stored = new SetterConnection();
        stored.setEnabled(false);

        guardedMapper().map(request(true, "new"), stored);

        assertThat(stored.getEnabled()).isFalse();
        assertThat(stored.getName()).isEqualTo("new");
    }

    @Test
    @DisplayName("without the condition the same update overwrites the stored value, which is the defect")
    void without_the_condition_the_value_is_overwritten() {
        Connection stored = new Connection();
        stored.enabled = false;

        plainMapper().map(request(true, "new"), stored);

        assertThat(stored.enabled).isTrue();
    }

    @Test
    @DisplayName("a DTO destination is still written: a read projection copies the value out of the entity")
    void reading_out_of_the_entity_is_not_skipped() {
        Connection stored = new Connection();
        stored.enabled = false;
        stored.name = "kept";
        FlaggedView view = new FlaggedView();
        view.enabled = true;

        guardedMapper().map(stored, view);

        assertThat(view.enabled).isFalse();
        assertThat(view.name).isEqualTo("kept");
    }

    @Test
    @DisplayName("installing twice leaves one condition, and the create-default condition still holds")
    void install_is_idempotent_and_composes() {
        ModelMapper mapper = guardedMapper();
        Object first = mapper.getConfiguration().getPropertyCondition();
        VerbOnlyWriteSkipCondition.install(mapper);
        assertThat(mapper.getConfiguration().getPropertyCondition()).isSameAs(first);

        Connection created = new Connection();
        mapper.map(new ConnectionCreateDTO(), created);
        assertThat(created.name).isEqualTo("unnamed");
        assertThat(created.enabled).isTrue();
    }

    @Test
    @DisplayName("the auto-configured mapper carries the condition")
    void auto_configured_mapper_carries_the_condition() {
        ModelMapper mapper = new SimpliXModelMapperAutoConfiguration().modelMapper();
        Connection stored = new Connection();
        stored.enabled = false;

        mapper.map(request(true, "new"), stored);

        assertThat(stored.enabled).isFalse();
        assertThat(stored.name).isEqualTo("new");
    }

    @Entity
    static class Connection {
        @VerbOnly private Boolean enabled = true;
        private String name = "unnamed";
    }

    @Entity
    static class SetterConnection {
        @VerbOnly private Boolean enabled = true;
        private String name;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /** Not an entity: a view a service fills from the entity, flagged so Meta publishes it. */
    static class FlaggedView {
        @VerbOnly private Boolean enabled;
        private String name;
    }

    static class ConnectionCreateDTO {
        private Boolean enabled;
        private String name;
    }

    static class ConnectionRequest {
        private Boolean enabled;
        private String name;
    }
}
