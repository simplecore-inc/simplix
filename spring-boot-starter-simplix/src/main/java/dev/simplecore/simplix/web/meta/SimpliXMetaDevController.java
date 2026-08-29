package dev.simplecore.simplix.web.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.meta.model.DtoMeta;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;

/**
 * Serves the SimpliX Meta document for frontend code generation. Registered only by
 * {@code SimpliXDevMetaAutoConfiguration} — property-gated, never under a production profile.
 * The mapped path sits under the same {@code /dev/**} convention as the i18n dev endpoint, so an
 * application's existing dev-profile permit rule covers it.
 */
@RestController
@RequestMapping("/dev/meta")
public class SimpliXMetaDevController {

    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectMapper objectMapper;
    private final List<SimpliXMetaContributor> contributors;

    public SimpliXMetaDevController(RequestMappingHandlerMapping handlerMapping,
                                    ObjectMapper objectMapper,
                                    List<SimpliXMetaContributor> contributors) {
        this.handlerMapping = handlerMapping;
        this.objectMapper = objectMapper;
        this.contributors = contributors;
    }

    @GetMapping("/dto")
    public SimpliXApiResponse<DtoMeta> dto() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        DtoMeta meta = new DtoMetaBuilder(objectMapper, contributors).build(handlerMethods);
        return SimpliXApiResponse.success(meta);
    }
}
