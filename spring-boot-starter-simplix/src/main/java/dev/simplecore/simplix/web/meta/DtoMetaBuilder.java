package dev.simplecore.simplix.web.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;
import dev.simplecore.simplix.web.meta.model.AccessMeta;
import dev.simplecore.simplix.web.meta.model.DtoMeta;
import dev.simplecore.simplix.web.meta.model.EnumMeta;
import dev.simplecore.simplix.web.meta.model.OperationMeta;
import dev.simplecore.simplix.web.meta.model.ParamMeta;
import dev.simplecore.simplix.web.meta.model.RequestMeta;
import dev.simplecore.simplix.web.meta.model.TypeMeta;
import dev.simplecore.simplix.web.meta.model.TypeRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Assembles the whole document: walks handler methods for operations, drains the type registry for
 * DTOs, registers enums, and lets contributors extend the result.
 */
public class DtoMetaBuilder {

    private static final String SEARCHABLE_PARAMS = "dev.simplecore.searchable.openapi.annotation.SearchableParams";
    private static final String SEARCH_CONDITION = "dev.simplecore.searchable.core.condition.SearchCondition";
    private static final String META_DEV_CONTROLLER = "dev.simplecore.simplix.web.meta.SimpliXMetaDevController";
    private static final String I18N_DEV_CONTROLLER = "dev.simplecore.simplix.web.i18n.I18nMessagesDevRestController";

    private final ObjectMapper mapper;
    private final List<SimpliXMetaContributor> contributors;
    private final MetaTypeRegistry registry = new MetaTypeRegistry();
    private final TypeRefMapper typeRefMapper = new TypeRefMapper(registry);

    public DtoMetaBuilder(ObjectMapper mapper, List<SimpliXMetaContributor> contributors) {
        this.mapper = mapper;
        this.contributors = contributors;
    }

    public DtoMeta build(Map<RequestMappingInfo, HandlerMethod> handlerMethods) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        List<OperationMeta> operations = new ArrayList<>();

        handlerMethods.forEach((info, handler) -> {
            if (skip(handler)) return;
            String path = info.getPathPatternsCondition() != null
                    && !info.getPathPatternsCondition().getPatterns().isEmpty()
                    ? info.getPathPatternsCondition().getPatterns().iterator().next().getPatternString()
                    : null;
            if (path == null) return;
            for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                OperationMeta operation = toOperation(path, method.name(), handler);
                operations.add(operation);
                contributors.forEach(c -> c.contributeOperation(handler, operation, extensions));
            }
        });

        Map<String, TypeMeta> types = new LinkedHashMap<>();
        TypeMetaFactory factory = new TypeMetaFactory(mapper, typeRefMapper, registry);
        while (registry.hasPending()) {
            Class<?> type = registry.next();
            String name = MetaTypeRegistry.typeName(type);
            TypeMeta meta = factory.create(type);
            types.put(name, meta);
            contributors.forEach(c -> c.contributeType(type, name, extensions));
        }

        Map<String, EnumMeta> enums = new LinkedHashMap<>();
        registry.enums().forEach((enumClass, name) -> enums.put(name, toEnumMeta(enumClass)));

        // Handler mappings arrive in hash order, and the type registry is drained in the order
        // those operations seeded it, so two runs of the same application emit the same document
        // with its three collections shuffled. A generator reading it then makes a different
        // choice wherever it has to pick between candidates, and a snapshot diff is noise from
        // end to end. Sorting here is what makes the document a function of the application.
        operations.sort(Comparator.comparing(OperationMeta::path).thenComparing(OperationMeta::method));

        return new DtoMeta(1, new TreeMap<>(enums), new TreeMap<>(types), operations,
                extensions.isEmpty() ? null : extensions);
    }

    /**
     * The framework's own dev controllers and infrastructure endpoints stay out of SimpliX Meta.
     * Matched by class NAME rather than by class literal, because the dev controllers are
     * created in a later task and by package prefix would also exclude this package's own
     * test fixtures.
     */
    private boolean skip(HandlerMethod handler) {
        Class<?> beanType = handler.getBeanType();
        String name = beanType.getName();
        String pkg = beanType.getPackageName();
        return name.equals(META_DEV_CONTROLLER)
                || name.equals(I18N_DEV_CONTROLLER)
                || pkg.startsWith("org.springdoc")
                || pkg.startsWith("org.springframework");
    }

    private OperationMeta toOperation(String path, String httpMethod, HandlerMethod handler) {
        Class<?> beanType = handler.getBeanType();
        // Same order as OperationIdCustomizer: stripping "Controller" first leaves "…Rest" on a
        // *RestController, which is what the OpenAPI operationId carries. Reversing these two lines
        // silently desynchronises every id from the published document.
        String controller = beanType.getSimpleName()
                .replaceFirst("Controller$", "")
                .replaceFirst("RestController$", "");
        String id = controller + "_" + handler.getMethod().getName();

        Tag tag = AnnotatedElementUtils.findMergedAnnotation(beanType, Tag.class);
        String tagName = tag != null ? tag.name() : beanType.getSimpleName();

        Operation openapi = handler.getMethodAnnotation(Operation.class);
        String summary = openapi != null && !openapi.summary().isEmpty() ? openapi.summary() : null;

        PreAuthorize pre = handler.getMethodAnnotation(PreAuthorize.class);
        if (pre == null) pre = AnnotatedElementUtils.findMergedAnnotation(beanType, PreAuthorize.class);
        AccessMeta access = AccessExpressionParser.parse(pre != null ? pre.value() : null);

        TypeRef body = null;
        String contentType = null;
        String searchDto = null;
        List<ParamMeta> query = new ArrayList<>();
        List<ParamMeta> pathParams = new ArrayList<>();

        for (MethodParameter parameter : handler.getMethodParameters()) {
            parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
            Class<?> parameterType = parameter.getParameterType();
            ResolvableType resolvable = ResolvableType.forMethodParameter(parameter);

            String searchableDtoName = searchableParamsDto(parameter);
            if (searchableDtoName != null) {
                searchDto = searchableDtoName;
                continue;
            }
            if (parameterType.getName().equals(SEARCH_CONDITION)) {
                Class<?> conditionDto = resolvable.getGeneric(0).resolve();
                if (conditionDto != null) searchDto = registry.register(conditionDto);
                continue;
            }
            if (parameter.hasParameterAnnotation(RequestBody.class)) {
                Class<?> bodyClass = resolvable.resolve();
                if (bodyClass != null && !Map.class.isAssignableFrom(bodyClass)) {
                    // map(), not register(): a body is often Set<XUpdateDTO> or List<XOrderDTO>,
                    // and resolve() would erase the element type the client has to send.
                    body = typeRefMapper.map(resolvable);
                }
                continue;
            }
            if (MultipartFile.class.isAssignableFrom(parameterType)) {
                contentType = "multipart";
                // Record the part under its form-field name. A client has to build a FormData and
                // append the file under exactly this name, and dropping the parameter left the
                // whole upload unrepresentable — TypeRef.file() exists for this.
                RequestParam filePart = parameter.getParameterAnnotation(RequestParam.class);
                String partName = filePart != null
                        ? firstNonEmpty(filePart.name(), filePart.value(), parameter.getParameterName())
                        : parameter.getParameterName();
                if (partName != null) {
                    query.add(new ParamMeta(partName, TypeRef.file(),
                            filePart == null || filePart.required(), null));
                }
                continue;
            }
            PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
            if (pathVariable != null) {
                String name = firstNonEmpty(pathVariable.name(), pathVariable.value(),
                        parameter.getParameterName());
                pathParams.add(new ParamMeta(name, typeRefMapper.map(resolvable), true, null));
                continue;
            }
            RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
            if (requestParam != null) {
                String name = firstNonEmpty(requestParam.name(), requestParam.value(),
                        parameter.getParameterName());
                query.add(new ParamMeta(name, typeRefMapper.map(resolvable),
                        requestParam.required(), null));
            }
        }
        if (body != null && contentType == null) contentType = "json";

        TypeRef response = typeRefMapper.map(
                ResolvableType.forMethodReturnType(handler.getMethod()));

        RequestMeta request = new RequestMeta(body, contentType, query, pathParams, searchDto);
        return new OperationMeta(id, httpMethod, path, tagName, summary, request, response, access);
    }

    /** Annotation name/value aliases are not merged on direct reads — check both, then bytecode names. */
    private static String firstNonEmpty(String first, String second, String fallback) {
        if (first != null && !first.isEmpty()) return first;
        if (second != null && !second.isEmpty()) return second;
        return fallback;
    }

    private String searchableParamsDto(MethodParameter parameter) {
        for (Annotation annotation : parameter.getParameterAnnotations()) {
            if (!annotation.annotationType().getName().equals(SEARCHABLE_PARAMS)) continue;
            try {
                Class<?> dto = (Class<?>) annotation.annotationType()
                        .getMethod("value").invoke(annotation);
                return registry.register(dto);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to read @SearchableParams", e);
            }
        }
        return null;
    }

    private EnumMeta toEnumMeta(Class<?> enumClass) {
        boolean labeled = SimpliXLabeledEnum.class.isAssignableFrom(enumClass);
        String simpleName = enumClass.getSimpleName();
        List<EnumMeta.Value> values = Arrays.stream(enumClass.getEnumConstants())
                .map(constant -> new EnumMeta.Value(((Enum<?>) constant).name(),
                        labeled ? "enums." + simpleName + "." + ((Enum<?>) constant).name() : null))
                .toList();
        return new EnumMeta(labeled, values);
    }
}
