package dev.simplecore.simplix.web.mapping;

import dev.simplecore.simplix.springboot.autoconfigure.SimpliXModelMapperAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.Condition;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration.AccessLevel;
import org.modelmapper.convention.MatchingStrategies;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateRequestNullSkipConditionTest {

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
        return mapper;
    }

    @Test
    @DisplayName("a create request that leaves a field out keeps the entity default")
    void create_without_a_field_keeps_the_default() {
        Widget widget = new Widget();

        guardedMapper().map(new WidgetCreateDTO(), widget);

        assertThat(widget.displayOrder).isEqualTo(0);
        assertThat(widget.tags).isEmpty();
        assertThat(widget.note).isEqualTo("none");
    }

    @Test
    @DisplayName("a create request that names a field overrides the default")
    void create_with_a_field_overrides_the_default() {
        WidgetCreateDTO request = new WidgetCreateDTO();
        request.displayOrder = 7;
        request.note = "kept";
        Widget widget = new Widget();

        guardedMapper().map(request, widget);

        assertThat(widget.displayOrder).isEqualTo(7);
        assertThat(widget.note).isEqualTo("kept");
    }

    @Test
    @DisplayName("without the condition the same request wipes the default, which is the defect")
    void without_the_condition_the_default_is_lost() {
        Widget widget = new Widget();

        plainMapper().map(new WidgetCreateDTO(), widget);

        assertThat(widget.displayOrder).isNull();
        assertThat(widget.note).isNull();
    }

    @Test
    @DisplayName("an update request still clears a field with null, though it extends the create request")
    void update_keeps_null_as_a_way_to_clear() {
        Widget widget = new Widget();
        widget.displayOrder = 5;
        widget.note = "old";

        guardedMapper().map(new WidgetUpdateDTO(), widget);

        assertThat(widget.displayOrder).isNull();
        assertThat(widget.note).isNull();
    }

    @Test
    @DisplayName("a request class that is not a create request is copied as before")
    void other_requests_are_copied_as_before() {
        Widget widget = new Widget();

        guardedMapper().map(new WidgetFilter(), widget);

        assertThat(widget.displayOrder).isNull();
    }

    @Test
    @DisplayName("installing twice leaves one condition, and an existing condition is kept")
    void install_is_idempotent_and_composes() {
        ModelMapper mapper = plainMapper();
        List<Object> seen = new ArrayList<>();
        Condition<Object, Object> existing = context -> {
            seen.add(context.getSource());
            return true;
        };
        mapper.getConfiguration().setPropertyCondition(existing);

        CreateRequestNullSkipCondition.install(mapper);
        Condition<?, ?> afterFirst = mapper.getConfiguration().getPropertyCondition();
        CreateRequestNullSkipCondition.install(mapper);

        assertThat(mapper.getConfiguration().getPropertyCondition()).isSameAs(afterFirst);
        Widget widget = new Widget();
        mapper.map(new WidgetCreateDTO(), widget);
        assertThat(seen).isNotEmpty();
        assertThat(widget.displayOrder).isEqualTo(0);
    }

    @Test
    @DisplayName("the auto-configured mapper keeps create defaults and still clears on update")
    void auto_configured_mapper_carries_the_condition() {
        ModelMapper mapper = new SimpliXModelMapperAutoConfiguration().modelMapper();

        Widget created = new Widget();
        mapper.map(new WidgetCreateDTO(), created);
        assertThat(created.displayOrder).isEqualTo(0);
        assertThat(created.note).isEqualTo("none");

        Widget updated = new Widget();
        updated.note = "old";
        mapper.map(new WidgetUpdateDTO(), updated);
        assertThat(updated.note).isNull();
    }

    /** An entity the way the domain declares them: defaults in place before any request is copied. */
    static class Widget {
        private Integer displayOrder = 0;
        private String note = "none";
        private List<String> tags = new ArrayList<>();
    }

    static class WidgetCreateDTO {
        private Integer displayOrder;
        private String note;
        private List<String> tags;
    }

    static class WidgetUpdateDTO extends WidgetCreateDTO {
        private String widgetId;
    }

    static class WidgetFilter {
        private Integer displayOrder;
    }
}
