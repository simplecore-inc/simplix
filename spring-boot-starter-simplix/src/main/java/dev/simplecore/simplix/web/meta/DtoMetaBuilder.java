package dev.simplecore.simplix.web.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.annotation.Tone;
import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;
import dev.simplecore.simplix.core.i18n.EntityMessageResolver;
import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import dev.simplecore.simplix.web.controller.SimpliXDefaultSort;
import dev.simplecore.simplix.web.controller.SimpliXListFigures;
import dev.simplecore.simplix.web.i18n.I18nMessagesService;
import dev.simplecore.simplix.core.entity.annotation.MappingEntity;
import dev.simplecore.simplix.web.meta.model.EntityMeta;
import dev.simplecore.simplix.web.meta.model.FieldMeta;
import dev.simplecore.simplix.web.meta.model.HolderMeta;
import dev.simplecore.simplix.web.meta.model.AccessMeta;
import dev.simplecore.simplix.web.meta.model.AggregateMeta;
import dev.simplecore.simplix.web.meta.model.DefaultSortMeta;
import dev.simplecore.simplix.web.meta.model.DtoMeta;
import dev.simplecore.simplix.web.meta.model.EnumMeta;
import dev.simplecore.simplix.web.meta.model.OperationMeta;
import dev.simplecore.simplix.web.meta.model.ParamMeta;
import dev.simplecore.simplix.web.meta.model.RequestMeta;
import dev.simplecore.simplix.web.meta.model.TypeMeta;
import dev.simplecore.simplix.web.meta.model.TypeRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

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
    private final Function<String, Map<String, String>> labelResolver;

    public DtoMetaBuilder(ObjectMapper mapper, List<SimpliXMetaContributor> contributors) {
        this(mapper, contributors, DtoMetaBuilder::resolveFromBundles);
    }

    /**
     * @param labelResolver resolves one message key to its text per language code, omitting the
     *                      languages where the key is not declared
     */
    public DtoMetaBuilder(ObjectMapper mapper, List<SimpliXMetaContributor> contributors,
                          Function<String, Map<String, String>> labelResolver) {
        this.mapper = mapper;
        this.contributors = contributors;
        this.labelResolver = labelResolver;
    }

    public DtoMeta build(Map<RequestMappingInfo, HandlerMethod> handlerMethods) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        List<OperationMeta> operations = new ArrayList<>();
        Map<OperationMeta, SimpliXDefaultSort> operationSorts = new IdentityHashMap<>();
        Map<String, Class<?>> entityClasses = new TreeMap<>();
        Map<String, SimpliXDefaultSort> defaultSorts = new TreeMap<>();
        Map<String, SimpliXListFigures> figures = new TreeMap<>();
        handlerMethods.values().forEach(handler -> {
            if (skip(handler)) return;
            Class<?> entity = entityOf(handler.getBeanType());
            if (entity == null) return;
            entityClasses.put(entity.getSimpleName(), entity);
            SimpliXDefaultSort sort = AnnotatedElementUtils.findMergedAnnotation(
                    handler.getBeanType(), SimpliXDefaultSort.class);
            if (sort != null) defaultSorts.put(entity.getSimpleName(), sort);
            SimpliXListFigures declared = AnnotatedElementUtils.findMergedAnnotation(
                    handler.getBeanType(), SimpliXListFigures.class);
            if (declared != null) figures.put(entity.getSimpleName(), declared);
        });

        handlerMethods.forEach((info, handler) -> {
            if (skip(handler)) return;
            String path = info.getPathPatternsCondition() != null
                    && !info.getPathPatternsCondition().getPatterns().isEmpty()
                    ? info.getPathPatternsCondition().getPatterns().iterator().next().getPatternString()
                    : null;
            if (path == null) return;
            for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                OperationMeta operation = toOperation(path, method.name(), handler, info);
                operations.add(operation);
                SimpliXDefaultSort sort = operation.request().searchDto() == null ? null
                        : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), SimpliXDefaultSort.class);
                if (sort != null) operationSorts.put(operation, sort);
                contributors.forEach(c -> c.contributeOperation(handler, operation, extensions));
            }
        });

        Map<String, TypeMeta> types = new LinkedHashMap<>();
        TypeMetaFactory factory = new TypeMetaFactory(mapper, typeRefMapper, registry, entityClasses);
        while (registry.hasPending()) {
            Class<?> type = registry.next();
            String name = MetaTypeRegistry.typeName(type);
            TypeMeta meta = withFieldLabels(factory.create(type));
            types.put(name, meta);
            contributors.forEach(c -> c.contributeType(type, name, extensions));
        }

        Map<String, EnumMeta> enums = new LinkedHashMap<>();
        registry.enums().forEach((enumClass, name) -> enums.put(name, toEnumMeta(enumClass)));

        // Handler mappings arrive in hash order, and the type registry is drained in the order
        // those operations seeded it, so two runs of the same application emit the same document
        // with its three collections shuffled. A generator reading it then makes a different
        // choice wherever it has to pick between candidates, and a snapshot diff is noise from
        // end to end. Sorting here is what makes the document a function of the application. Two
        // handlers can share a path and a method when their mappings differ by a parameter or a
        // media type, and the sort is stable, so the id decides where the path and method do not.
        operations.sort(Comparator.comparing(OperationMeta::path)
                .thenComparing(OperationMeta::method)
                .thenComparing(OperationMeta::id));

        Map<String, EntityMeta> entities = entities(entityClasses, defaultSorts, figures, types, operations);

        // Each search operation carries its own controller's order, checked against its own search DTO.
        operations.replaceAll(operation -> {
            SimpliXDefaultSort sort = operationSorts.get(operation);
            if (sort == null) return operation;
            return operation.withDefaultSort(defaultSort(operation.entity(), entityClasses.get(operation.entity()),
                    sort, types, List.of(operation)));
        });

        return new DtoMeta(1, new TreeMap<>(enums), new TreeMap<>(types), operations,
                extensions.isEmpty() ? null : extensions, entities.isEmpty() ? null : entities);
    }

    /** The entity a {@code SimpliXBaseController} subclass serves, or {@code null} for any other controller. */
    static Class<?> entityOf(Class<?> controller) {
        if (!SimpliXBaseController.class.isAssignableFrom(controller)) return null;
        return ResolvableType.forClass(controller).as(SimpliXBaseController.class).getGeneric(0).resolve();
    }

    /**
     * One {@link EntityMeta} per served entity, its holders derived from the references in the
     * search DTOs: a search DTO of entity {@code H} whose field references entity {@code T} makes
     * {@code H} a holder of {@code T}, listed by that operation filtered on the field.
     */
    private Map<String, EntityMeta> entities(Map<String, Class<?>> entityClasses,
                                             Map<String, SimpliXDefaultSort> defaultSorts,
                                             Map<String, SimpliXListFigures> figures,
                                             Map<String, TypeMeta> types,
                                             List<OperationMeta> operations) {
        Map<String, List<HolderMeta>> holders = new TreeMap<>();
        for (OperationMeta operation : operations) {
            String searchDto = operation.request().searchDto();
            if (searchDto == null || operation.entity() == null) continue;
            Class<?> holderClass = entityClasses.get(operation.entity());
            // A mapping row references both ends it joins; it is never an owner-like holder.
            if (holderClass != null && holderClass.isAnnotationPresent(MappingEntity.class)) continue;
            TypeMeta dto = types.get(searchDto);
            if (dto == null) continue;
            for (FieldMeta field : dto.fields()) {
                // A reference by kind derives no holder: the id alone would list every kind's records.
                if (field.reference() == null || field.reference().byKind() || field.searchable() == null
                        || field.searchable().operators().isEmpty()) continue;
                List<HolderMeta> list = holders.computeIfAbsent(field.reference().entity(), k -> new ArrayList<>());
                boolean known = list.stream().anyMatch(h -> h.entity().equals(operation.entity())
                        && h.field().equals(field.name()));
                // One holder per (entity, field): the GET and POST search of one controller are the
                // same listing, and operations are sorted, so the first one met is stable.
                if (!known) {
                    list.add(new HolderMeta(operation.entity(), field.name(),
                            field.searchable().operators(), operation.id()));
                }
            }
        }

        Map<String, EntityMeta> result = new TreeMap<>();
        entityClasses.forEach((name, type) -> {
            String labelKey = "entities." + name;
            String pluralKey = labelKey + "." + I18nMessagesService.ENTITY_ATTRIBUTE_PREFIX + "plural";
            Map<String, String> label = labelResolver.apply(labelKey);
            String descriptionKey = labelKey + "." + I18nMessagesService.ENTITY_ATTRIBUTE_PREFIX + "description";
            Map<String, String> plural = labelResolver.apply(pluralKey);
            Map<String, String> description = labelResolver.apply(descriptionKey);
            List<HolderMeta> held = holders.get(name);
            result.put(name, new EntityMeta(type.getName(), EntityReferences.idFieldOf(type),
                    EntityReferences.nameFieldsOf(type),
                    labelKey, pluralKey, descriptionKey,
                    label.isEmpty() ? null : label,
                    plural.isEmpty() ? null : plural,
                    description.isEmpty() ? null : description,
                    held == null ? null : List.copyOf(held),
                    defaultSort(name, type, defaultSorts.get(name), types, operations),
                    statusCounts(name, type, figures.get(name), types, operations),
                    aggregates(name, type, figures.get(name), types, operations)));
        });
        return result;
    }

    /**
     * The fields the entity's status counts accept, or {@code null} when it declares none.
     *
     * @throws IllegalStateException when a field is neither an entity field nor a field of its search DTO
     */
    static List<String> statusCounts(String entity, Class<?> type, SimpliXListFigures declared,
                                     Map<String, TypeMeta> types, List<OperationMeta> operations) {
        if (declared == null || declared.statusCounts().length == 0) return null;
        for (String field : declared.statusCounts()) {
            requireKnownField(entity, type, field, types, operations);
        }
        return List.of(declared.statusCounts());
    }

    /**
     * The figures the entity's aggregates return, in order, or {@code null} when it declares none.
     *
     * @throws IllegalStateException when a field is neither an entity field nor a field of its search DTO
     */
    static List<AggregateMeta> aggregates(String entity, Class<?> type, SimpliXListFigures declared,
                                          Map<String, TypeMeta> types, List<OperationMeta> operations) {
        if (declared == null || declared.aggregates().length == 0) return null;
        for (SimpliXListFigures.Figure figure : declared.aggregates()) {
            requireKnownField(entity, type, figure.field(), types, operations);
        }
        return Arrays.stream(declared.aggregates())
                .map(figure -> new AggregateMeta(figure.field(), figure.op()))
                .toList();
    }

    /**
     * A figure counts or sums an entity field or a search DTO field (a derived filter the service
     * resolves itself); a name that is neither would answer every request with an error.
     */
    private static void requireKnownField(String entity, Class<?> type, String field,
                                          Map<String, TypeMeta> types, List<OperationMeta> operations) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (Arrays.stream(current.getDeclaredFields()).anyMatch(f -> f.getName().equals(field))) return;
        }
        boolean searchable = operations.stream()
                .filter(op -> entity.equals(op.entity()) && op.request().searchDto() != null)
                .map(op -> types.get(op.request().searchDto()))
                .filter(Objects::nonNull)
                .flatMap(dto -> dto.fields().stream())
                .anyMatch(f -> f.name().equals(field));
        if (!searchable) {
            throw new IllegalStateException("List figures of " + entity + " name '" + field
                    + "', which is neither a field of the entity nor of its search DTO");
        }
    }

    /**
     * The order the entity's list opens in, checked against its search DTO.
     *
     * @throws IllegalStateException when the field is the entity's id, is not a sortable field of
     *                               the search DTO, or the direction is neither {@code asc} nor
     *                               {@code desc}: a list opened that way is refused or unordered
     */
    static DefaultSortMeta defaultSort(String entity, Class<?> type, SimpliXDefaultSort declared,
                                       Map<String, TypeMeta> types, List<OperationMeta> operations) {
        if (declared == null) return null;
        String field = declared.field();
        String direction = declared.direction().toLowerCase(Locale.ROOT);
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw new IllegalStateException("Default sort of " + entity + " has direction '"
                    + declared.direction() + "'; it must be asc or desc");
        }
        if (field.equals(EntityReferences.idFieldOf(type))) {
            throw new IllegalStateException("Default sort of " + entity + " names its id field '"
                    + field + "'; an id orders a list in no order a reader can use");
        }
        boolean sortable = operations.stream()
                .filter(op -> entity.equals(op.entity()) && op.request().searchDto() != null)
                .map(op -> types.get(op.request().searchDto()))
                .filter(Objects::nonNull)
                .flatMap(dto -> dto.fields().stream())
                .filter(f -> f.name().equals(field) && f.searchable() != null)
                .anyMatch(f -> f.searchable().sortable()
                        || (f.searchable().sortAlias() != null && !f.searchable().sortAlias().isEmpty()));
        if (!sortable) {
            throw new IllegalStateException("Default sort of " + entity + " names '" + field
                    + "', which its search DTO does not declare sortable");
        }
        return new DefaultSortMeta(field, direction);
    }

    /** Resolves a key in every language the entity and enum bundles ship. */
    /**
     * Resolves a key from the entity label bundles first and then from {@code messageSource}, so a
     * field labelled through an application bundle ({@code {field.count}} in a bean validation
     * bundle, say) carries its text in every language too.
     *
     * @param messageSource the application's message source
     * @return a resolver for {@link #DtoMetaBuilder(ObjectMapper, List, Function)}
     */
    public static Function<String, Map<String, String>> bundlesThen(MessageSource messageSource) {
        return key -> {
            Map<String, String> resolved = resolveFromBundles(key);
            for (String language : I18nMessagesService.detectSupportedLocales()) {
                if (resolved.containsKey(language)) continue;
                String text = messageSource.getMessage(key, null, null, Locale.forLanguageTag(language));
                if (text != null && !text.equals(key)) resolved.put(language, text);
            }
            return resolved;
        };
    }

    /** Each field whose {@code @FieldLabel} names a key gains the key's text per language. */
    private TypeMeta withFieldLabels(TypeMeta type) {
        if (type.fields() == null) return type;
        List<FieldMeta> fields = type.fields().stream().map(field -> {
            if (field.labelKey() == null) return field;
            Map<String, String> labels = labelResolver.apply(field.labelKey());
            return labels.isEmpty() ? field : field.withLabels(labels);
        }).toList();
        return new TypeMeta(type.javaClass(), type.extendsType(), type.typeParams(), type.description(),
                fields, type.entity());
    }

    private static Map<String, String> resolveFromBundles(String key) {
        Map<String, String> resolved = new TreeMap<>();
        for (String language : I18nMessagesService.detectSupportedLocales()) {
            String text = EntityMessageResolver.resolveMessage(key, Locale.forLanguageTag(language));
            if (text != null && !text.equals(key)) resolved.put(language, text);
        }
        return resolved;
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

    private OperationMeta toOperation(String path, String httpMethod, HandlerMethod handler,
                                      RequestMappingInfo info) {
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
                // whole upload unrepresentable: TypeRef.file() exists for this.
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

        TypeRef response = writesItsOwnBody(handler, info)
                ? TypeRef.binary()
                : typeRefMapper.map(ResolvableType.forMethodReturnType(handler.getMethod()));

        RequestMeta request = new RequestMeta(body, contentType, query, pathParams, searchDto);
        Class<?> entity = entityOf(beanType);
        return new OperationMeta(id, httpMethod, path, tagName, summary, request, response, access,
                entity != null ? entity.getSimpleName() : null);
    }

    /**
     * Whether the handler answers with bytes rather than a JSON envelope: its mapping produces only
     * non-JSON media types ({@code produces = "text/csv"}), or it returns nothing and writes the
     * body into the {@link ServletResponse} it takes. A client unwrapping either as JSON fails on
     * the first byte, so both are published as a {@code binary} response.
     */
    static boolean writesItsOwnBody(HandlerMethod handler, RequestMappingInfo info) {
        Set<MediaType> produces = info.getProducesCondition().getProducibleMediaTypes();
        if (!produces.isEmpty() && produces.stream().noneMatch(DtoMetaBuilder::isJson)) return true;
        Class<?> returned = handler.getMethod().getReturnType();
        if (returned != void.class && returned != Void.class) return false;
        return Arrays.stream(handler.getMethod().getParameterTypes())
                .anyMatch(ServletResponse.class::isAssignableFrom);
    }

    private static boolean isJson(MediaType type) {
        return type.isCompatibleWith(MediaType.APPLICATION_JSON)
                || (type.getSubtype() != null && type.getSubtype().endsWith("+json"));
    }

    /** Annotation name/value aliases are not merged on direct reads; check both, then bytecode names. */
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

    /** The {@code @Tone} on one constant, lower-cased, or {@code null} when it carries none. */
    private static String toneOf(Class<?> enumClass, String constant) {
        try {
            Tone tone = enumClass.getField(constant).getAnnotation(Tone.class);
            return tone != null ? tone.value().name().toLowerCase(Locale.ROOT) : null;
        } catch (NoSuchFieldException impossible) {
            throw new IllegalStateException("Enum constant without a field: " + constant, impossible);
        }
    }

    private EnumMeta toEnumMeta(Class<?> enumClass) {
        boolean labeled = SimpliXLabeledEnum.class.isAssignableFrom(enumClass);
        String simpleName = enumClass.getSimpleName();
        List<EnumMeta.Value> values = Arrays.stream(enumClass.getEnumConstants())
                .map(constant -> new EnumMeta.Value(((Enum<?>) constant).name(),
                        labeled ? "enums." + simpleName + "." + ((Enum<?>) constant).name() : null,
                        toneOf(enumClass, ((Enum<?>) constant).name())))
                .toList();
        return new EnumMeta(labeled, values);
    }
}
