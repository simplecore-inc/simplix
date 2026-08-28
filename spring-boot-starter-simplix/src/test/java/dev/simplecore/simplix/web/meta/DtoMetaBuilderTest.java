package dev.simplecore.simplix.web.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.enums.LabeledEnum;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.meta.model.DtoMeta;
import dev.simplecore.simplix.web.meta.model.OperationMeta;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DtoMetaBuilderTest {

    enum AreaKind implements LabeledEnum { AREA, ZONE }

    static class AreaCreateDTO {
        @NotBlank public String name;
        public AreaKind kind;
    }

    static class AreaListDTO {
        public String id;
        public String name;
    }

    @RestController
    @RequestMapping("/areas")
    @Tag(name = "site.Area")
    static class AreaRestController {

        @GetMapping("/{areaId}")
        @PreAuthorize("hasPermission('SAFETY_SITE', 'view')")
        public SimpliXApiResponse<AreaListDTO> get(@PathVariable("areaId") String areaId) { return null; }

        @PostMapping
        @PreAuthorize("hasPermission('SAFETY_SITE', 'edit')")
        public SimpliXApiResponse<AreaListDTO> create(@RequestBody AreaCreateDTO dto) { return null; }

        @GetMapping
        public SimpliXApiResponse<Page<AreaListDTO>> list(
                @RequestParam(name = "keyword", required = false) String keyword) { return null; }
    }

    private Map<RequestMappingInfo, HandlerMethod> handlerMethods() throws Exception {
        AreaRestController controller = new AreaRestController();
        RequestMappingInfo.BuilderConfiguration config = new RequestMappingInfo.BuilderConfiguration();
        config.setPatternParser(new PathPatternParser());
        Map<RequestMappingInfo, HandlerMethod> map = new LinkedHashMap<>();
        map.put(RequestMappingInfo.paths("/areas/{areaId}").methods(org.springframework.web.bind.annotation.RequestMethod.GET).options(config).build(),
                new HandlerMethod(controller, AreaRestController.class.getMethod("get", String.class)));
        map.put(RequestMappingInfo.paths("/areas").methods(org.springframework.web.bind.annotation.RequestMethod.POST).options(config).build(),
                new HandlerMethod(controller, AreaRestController.class.getMethod("create", AreaCreateDTO.class)));
        map.put(RequestMappingInfo.paths("/areas").methods(org.springframework.web.bind.annotation.RequestMethod.GET).options(config).build(),
                new HandlerMethod(controller, AreaRestController.class.getMethod("list", String.class)));
        return map;
    }

    private DtoMeta build(List<SimpliXMetaContributor> contributors) throws Exception {
        return new DtoMetaBuilder(new ObjectMapper(), contributors).build(handlerMethods());
    }

    private OperationMeta operation(DtoMeta meta, String id) {
        return meta.operations().stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void operationIdTagAccessAndResponse() throws Exception {
        DtoMeta meta = build(List.of());
        OperationMeta get = operation(meta, "Area_get");
        assertThat(get.method()).isEqualTo("GET");
        assertThat(get.path()).isEqualTo("/areas/{areaId}");
        assertThat(get.tag()).isEqualTo("site.Area");
        assertThat(get.access().kind()).isEqualTo("permission");
        assertThat(get.access().action()).isEqualTo("view");
        assertThat(get.request().path()).extracting(p -> p.name()).containsExactly("areaId");
        assertThat(get.response().name()).isEqualTo("SimpliXApiResponse");
    }

    @Test
    void bodyTypeAndReachableTypesRegister() throws Exception {
        DtoMeta meta = build(List.of());
        assertThat(operation(meta, "Area_create").request().body()).isEqualTo("AreaCreateDTO");
        assertThat(meta.types()).containsKeys("AreaCreateDTO", "AreaListDTO");
        assertThat(meta.enums().get("AreaKind").labeled()).isTrue();
        assertThat(meta.enums().get("AreaKind").values())
            .extracting(v -> v.name()).containsExactly("AREA", "ZONE");
    }

    @Test
    void queryParamsAndDefaultAccess() throws Exception {
        DtoMeta meta = build(List.of());
        OperationMeta list = operation(meta, "Area_list");
        assertThat(list.request().query()).extracting(p -> p.name()).containsExactly("keyword");
        assertThat(list.access().kind()).isEqualTo("authenticated");
    }

    @Test
    void contributorsWriteIntoExtensions() throws Exception {
        SimpliXMetaContributor contributor = new SimpliXMetaContributor() {
            @Override
            public void contributeOperation(HandlerMethod handler, OperationMeta operation,
                                            Map<String, Object> extensions) {
                extensions.put("op:" + operation.id(), "seen");
            }
        };
        DtoMeta meta = build(List.of(contributor));
        assertThat(meta.extensions()).containsKey("op:Area_get");
    }
}
