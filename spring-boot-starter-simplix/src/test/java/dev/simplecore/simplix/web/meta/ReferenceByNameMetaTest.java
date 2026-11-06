package dev.simplecore.simplix.web.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.annotation.References;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import dev.simplecore.simplix.web.meta.model.DtoMeta;
import dev.simplecore.simplix.web.meta.model.FieldMeta;
import dev.simplecore.simplix.web.meta.model.HolderMeta;
import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** A reference by entity name, read through the whole Meta build as a client receives it. */
class ReferenceByNameMetaTest {

    static class Tier {
        @Id String tierId;
    }

    static class Holder {
        @Id String holderId;
        @References(entity = "Tier") String tierId;
    }

    static class Stray {
        @Id String strayId;
        @References(entity = "Ledger") String ledgerId;
    }

    static class TierDTOs {
        static class TierSearchDTO {
            @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.IN}, sortable = true)
            public String tierId;
        }

        static class TierListDTO {
            public String tierId;
        }
    }

    static class HolderDTOs {
        static class HolderSearchDTO {
            @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.IN}, sortable = true)
            public String holderId;
            @SearchableField(entityField = "tierId", operators = {SearchOperator.EQUALS, SearchOperator.IN})
            public String tierId;
        }

        static class HolderListDTO {
            public String holderId;
            public String tierId;
        }
    }

    static class StrayDTOs {
        static class StrayListDTO {
            public String strayId;
            public String ledgerId;
        }

        static class StraySearchDTO {
            @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.IN}, sortable = true)
            public String strayId;
        }
    }

    static class TierRestController extends SimpliXBaseController<Tier, String> {
        TierRestController() { super(null); }

        @PostMapping("/search")
        public SimpliXApiResponse<Page<TierDTOs.TierListDTO>> search(
                @RequestBody SearchCondition<TierDTOs.TierSearchDTO> condition) { return null; }
    }

    static class HolderRestController extends SimpliXBaseController<Holder, String> {
        HolderRestController() { super(null); }

        @PostMapping("/search")
        public SimpliXApiResponse<Page<HolderDTOs.HolderListDTO>> search(
                @RequestBody SearchCondition<HolderDTOs.HolderSearchDTO> condition) { return null; }
    }

    static class StrayRestController extends SimpliXBaseController<Stray, String> {
        StrayRestController() { super(null); }

        @PostMapping("/search")
        public SimpliXApiResponse<Page<StrayDTOs.StrayListDTO>> search(
                @RequestBody SearchCondition<StrayDTOs.StraySearchDTO> condition) { return null; }
    }

    private static DtoMeta build(Map<String, Object> controllers) throws Exception {
        RequestMappingInfo.BuilderConfiguration config = new RequestMappingInfo.BuilderConfiguration();
        config.setPatternParser(new PathPatternParser());
        Map<RequestMappingInfo, HandlerMethod> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : controllers.entrySet()) {
            map.put(RequestMappingInfo.paths(entry.getKey()).methods(RequestMethod.POST).options(config).build(),
                    new HandlerMethod(entry.getValue(), entry.getValue().getClass().getMethod("search", SearchCondition.class)));
        }
        return new DtoMetaBuilder(new ObjectMapper(), List.of(), key -> Map.of()).build(map);
    }

    private static FieldMeta field(DtoMeta meta, String type, String name) {
        return meta.types().get(type).fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void aReferenceByNamePublishesTheTargetAndMakesItsHolder() throws Exception {
        Map<String, Object> controllers = new LinkedHashMap<>();
        controllers.put("/tiers/search", new TierRestController());
        controllers.put("/holders/search", new HolderRestController());
        DtoMeta meta = build(controllers);

        FieldMeta tierId = field(meta, "HolderListDTO", "tierId");
        assertThat(tierId.reference()).isNotNull();
        assertThat(tierId.reference().entity()).isEqualTo("Tier");
        assertThat(tierId.reference().idField()).isEqualTo("tierId");
        assertThat(tierId.reference().many()).isFalse();

        List<HolderMeta> holders = meta.entities().get("Tier").holders();
        assertThat(holders).extracting(HolderMeta::entity, HolderMeta::field).containsExactly(
                tuple("Holder", "tierId"));
    }

    @Test
    void aNameNoControllerServesFailsTheBuild() {
        Map<String, Object> controllers = new LinkedHashMap<>();
        controllers.put("/strays/search", new StrayRestController());
        assertThatThrownBy(() -> build(controllers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Stray.class.getName() + ".ledgerId")
                .hasMessageContaining("\"Ledger\"");
    }
}
