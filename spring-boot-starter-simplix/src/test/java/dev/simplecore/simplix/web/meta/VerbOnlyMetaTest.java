package dev.simplecore.simplix.web.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.simplix.core.annotation.VerbOnly;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import dev.simplecore.simplix.web.meta.model.DtoMeta;
import dev.simplecore.simplix.web.meta.model.FieldMeta;
import dev.simplecore.simplix.web.meta.model.TypeMeta;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code verbOnly}: a field whose value moves only through a verb, published on every DTO that carries it. */
class VerbOnlyMetaTest {

    static class Plan {
        @Id String planId;
        @VerbOnly Boolean listed = true;
        @VerbOnly Boolean archived = false;
        String planName;
    }

    static class PlanDTOs {
        static class PlanSearchDTO {
            @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.IN}, sortable = true)
            public String planId;
            @SearchableField(entityField = "listed", operators = {SearchOperator.EQUALS})
            public Boolean listed;
        }

        static class PlanCreateDTO {
            public String planName;
            public Boolean listed;
        }

        static class PlanUpdateDTO extends PlanCreateDTO {
            public String planId;
        }

        static class PlanDetailDTO {
            public String planId;
            public String planName;
            public Boolean listed;
            public Boolean archived;
            @VerbOnly public Boolean featured;
        }

        static class PlanStrictCreateDTO {
            public String planName;
            @NotNull public Boolean listed;
        }
    }

    static class PlanRestController extends SimpliXBaseController<Plan, String> {
        PlanRestController() { super(null); }

        @PostMapping("/search")
        public SimpliXApiResponse<Page<PlanDTOs.PlanDetailDTO>> search(
                @RequestBody SearchCondition<PlanDTOs.PlanSearchDTO> condition) { return null; }

        @PostMapping("/create")
        public SimpliXApiResponse<PlanDTOs.PlanDetailDTO> create(@RequestBody PlanDTOs.PlanCreateDTO body) { return null; }

        @PutMapping("/{planId}")
        public SimpliXApiResponse<PlanDTOs.PlanDetailDTO> update(@PathVariable("planId") String planId,
                                                                  @RequestBody PlanDTOs.PlanUpdateDTO body) { return null; }

        @GetMapping("/{planId}")
        public SimpliXApiResponse<PlanDTOs.PlanDetailDTO> get(@PathVariable("planId") String planId) { return null; }
    }

    static class StrictPlanRestController extends SimpliXBaseController<Plan, String> {
        StrictPlanRestController() { super(null); }

        @PostMapping("/create")
        public SimpliXApiResponse<PlanDTOs.PlanDetailDTO> create(@RequestBody PlanDTOs.PlanStrictCreateDTO body) { return null; }
    }

    private static DtoMeta build(Object controller, Map<String, Method> routes) {
        RequestMappingInfo.BuilderConfiguration config = new RequestMappingInfo.BuilderConfiguration();
        config.setPatternParser(new PathPatternParser());
        Map<RequestMappingInfo, HandlerMethod> map = new LinkedHashMap<>();
        routes.forEach((route, method) -> {
            String[] parts = route.split(" ", 2);
            map.put(RequestMappingInfo.paths(parts[1]).methods(RequestMethod.valueOf(parts[0])).options(config).build(),
                    new HandlerMethod(controller, method));
        });
        return new DtoMetaBuilder(new ObjectMapper(), List.of(), key -> Map.of()).build(map);
    }

    private static DtoMeta plans() throws Exception {
        Map<String, Method> routes = new LinkedHashMap<>();
        routes.put("POST /plans/search", PlanRestController.class.getMethod("search", SearchCondition.class));
        routes.put("POST /plans/create", PlanRestController.class.getMethod("create", PlanDTOs.PlanCreateDTO.class));
        routes.put("PUT /plans/{planId}", PlanRestController.class.getMethod("update", String.class, PlanDTOs.PlanUpdateDTO.class));
        routes.put("GET /plans/{planId}", PlanRestController.class.getMethod("get", String.class));
        return build(new PlanRestController(), routes);
    }

    private static FieldMeta field(DtoMeta meta, String type, String name) {
        TypeMeta found = meta.types().get(type);
        assertThat(found).as(type).isNotNull();
        return found.fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void aVerbOnlyEntityFieldIsPublishedOnEveryDtoThatCarriesIt() throws Exception {
        DtoMeta meta = plans();
        assertThat(field(meta, "PlanSearchDTO", "listed").verbOnly()).isTrue();
        assertThat(field(meta, "PlanCreateDTO", "listed").verbOnly()).isTrue();
        assertThat(field(meta, "PlanDetailDTO", "listed").verbOnly()).isTrue();
        assertThat(field(meta, "PlanDetailDTO", "archived").verbOnly()).isTrue();
    }

    @Test
    void anUpdateDtoInheritsTheFlagFromTheCreateDtoItExtends() throws Exception {
        DtoMeta meta = plans();
        assertThat(meta.types().get("PlanUpdateDTO").extendsType()).isEqualTo("PlanCreateDTO");
        assertThat(field(meta, "PlanUpdateDTO", "planId").verbOnly()).isNull();
    }

    @Test
    void aDerivedDtoFieldCarriesItsOwnFlag() throws Exception {
        assertThat(field(plans(), "PlanDetailDTO", "featured").verbOnly()).isTrue();
    }

    @Test
    void anOrdinaryFieldCarriesNoFlag() throws Exception {
        DtoMeta meta = plans();
        assertThat(field(meta, "PlanDetailDTO", "planName").verbOnly()).isNull();
        assertThat(field(meta, "PlanCreateDTO", "planName").verbOnly()).isNull();
    }

    @Test
    void theCreateFormStillReadsTheEntityDefaultOfAVerbOnlyField() throws Exception {
        DtoMeta meta = plans();
        assertThat(field(meta, "PlanCreateDTO", "listed").defaultValue()).isEqualTo(true);
        assertThat(field(meta, "PlanDetailDTO", "archived").defaultValue()).isEqualTo(false);
    }

    @Test
    void theFlagSerializesAsTrueAndIsAbsentOtherwise() throws Exception {
        ObjectMapper json = new ObjectMapper();
        DtoMeta meta = plans();
        assertThat(json.writeValueAsString(field(meta, "PlanDetailDTO", "listed"))).contains("\"verbOnly\":true");
        assertThat(json.writeValueAsString(field(meta, "PlanDetailDTO", "planName"))).doesNotContain("verbOnly");
    }

    @Test
    void aRequestDtoThatRequiresAVerbOnlyFieldFailsTheBuildNamingIt() throws Exception {
        Map<String, Method> routes = Map.of("POST /strict-plans/create",
                StrictPlanRestController.class.getMethod("create", PlanDTOs.PlanStrictCreateDTO.class));
        assertThatThrownBy(() -> build(new StrictPlanRestController(), routes))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PlanStrictCreateDTO.listed")
                .hasMessageContaining("@VerbOnly");
    }
}
