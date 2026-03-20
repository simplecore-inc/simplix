package dev.simplecore.simplix.web.config.openapi;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAPI operation customizer that resolves generic type information
 * for response schemas using Spring's {@link ResolvableType}.
 *
 * <p>Java type erasure causes generic type parameters to be lost at runtime,
 * resulting in springdoc generating incomplete schemas for wrapped responses
 * like {@code SimpliXApiResponse<Page<UserDto>>}. This customizer recovers
 * the full generic type chain from the handler method's return type and builds
 * accurate OpenAPI schemas without requiring any annotations.
 *
 * <p>Pre-configured wrapper types:
 * <ul>
 *   <li>{@link SimpliXApiResponse} - SimpliX standard API response wrapper</li>
 *   <li>{@code Page} / {@code Slice} - Spring Data pagination (optional dependency)</li>
 *   <li>{@link java.util.List}, {@link java.util.Set}, {@link Collection} - Collection types</li>
 *   <li>{@link Map} - Map types</li>
 *   <li>{@link ResponseEntity}, {@link Optional} - Transparent unwrap</li>
 * </ul>
 *
 * <p>All other types are treated as DTOs and referenced via {@code $ref}.
 * Use {@link DtoSchemaAutoRegistrar} alongside this customizer to ensure
 * all referenced DTOs are registered in OpenAPI components.
 *
 * <p>Configuration:
 * <pre>
 * simplix.swagger.customizers.generic-response.enabled=true   # default: true
 * simplix.swagger.customizers.generic-response.auto-wrap=true # default: true
 * </pre>
 *
 * <p>When {@code auto-wrap} is enabled, return types that are not already
 * {@link SimpliXApiResponse} are automatically wrapped with the ApiResponse
 * schema structure, matching the behavior of {@code SimpliXResponseBodyAdvice}.
 */
public class GenericResponseSchemaCustomizer implements OperationCustomizer, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GenericResponseSchemaCustomizer.class);

    private static final Class<?> PAGE_CLASS;
    private static final Class<?> SLICE_CLASS;

    static {
        Class<?> pageClass = null;
        Class<?> sliceClass = null;
        try {
            pageClass = Class.forName("org.springframework.data.domain.Page");
        } catch (ClassNotFoundException ignored) {
        }
        try {
            sliceClass = Class.forName("org.springframework.data.domain.Slice");
        } catch (ClassNotFoundException ignored) {
        }
        PAGE_CLASS = pageClass;
        SLICE_CLASS = sliceClass;
    }

    private final boolean autoWrapEnabled;

    /**
     * @param autoWrapEnabled when true, wraps non-SimpliXApiResponse return types
     *                        with SimpliXApiResponse schema structure
     */
    public GenericResponseSchemaCustomizer(boolean autoWrapEnabled) {
        this.autoWrapEnabled = autoWrapEnabled;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        try {
            return doCustomize(operation, handlerMethod);
        } catch (Exception e) {
            log.warn("Failed to customize operation for {}.{}: {}",
                    handlerMethod.getBeanType().getSimpleName(),
                    handlerMethod.getMethod().getName(),
                    e.getMessage());
            return operation;
        }
    }

    private Operation doCustomize(Operation operation, HandlerMethod handlerMethod) {
        if (hasExplicitResponseSchema(handlerMethod.getMethod())) {
            log.trace("Skipping customization for {}.{} - explicit @ApiResponse schema defined",
                    handlerMethod.getBeanType().getSimpleName(),
                    handlerMethod.getMethod().getName());
            return operation;
        }

        ResolvableType returnType = ResolvableType.forMethodReturnType(handlerMethod.getMethod());
        returnType = unwrapTransparent(returnType);

        Class<?> rawClass = returnType.resolve();
        if (rawClass == null) {
            return operation;
        }

        if (rawClass == void.class || rawClass == Void.class) {
            if (autoWrapEnabled) {
                setResponseSchema(operation, buildApiResponseSchema(new ObjectSchema()));
            }
            return operation;
        }

        if (isStreamingType(rawClass)) {
            return operation;
        }

        boolean isApiResponse = SimpliXApiResponse.class.isAssignableFrom(rawClass);

        if (isApiResponse || containsKnownGenericWrapper(returnType)) {
            Schema<?> schema = resolveSchema(returnType);
            if (schema != null) {
                // When auto-wrap is enabled and return type is not already SimpliXApiResponse,
                // wrap with ApiResponse schema to match SimpliXResponseBodyAdvice runtime behavior
                if (autoWrapEnabled && !isApiResponse) {
                    schema = buildApiResponseSchema(schema);
                }
                setResponseSchema(operation, schema);
                log.trace("Resolved generic schema for {}.{}",
                        handlerMethod.getBeanType().getSimpleName(),
                        handlerMethod.getMethod().getName());
            }
        } else if (autoWrapEnabled) {
            Schema<?> innerSchema = resolveSchema(returnType);
            if (innerSchema != null) {
                setResponseSchema(operation, buildApiResponseSchema(innerSchema));
                log.trace("Auto-wrapped schema for {}.{}",
                        handlerMethod.getBeanType().getSimpleName(),
                        handlerMethod.getMethod().getName());
            }
        }

        return operation;
    }

    /**
     * Check if the handler method has explicit @ApiResponse annotations
     * with content definitions for the 200 response code.
     * If so, we should respect the developer's intent and not override it.
     */
    private boolean hasExplicitResponseSchema(Method method) {
        // Check single @ApiResponse directly on method (not inside @ApiResponses container)
        ApiResponse apiResponse = AnnotatedElementUtils.findMergedAnnotation(method, ApiResponse.class);
        if (apiResponse != null && isSuccessResponse(apiResponse) && hasContentDefinition(apiResponse)) {
            return true;
        }

        // Check @ApiResponses container on method (handles @Repeatable multiple @ApiResponse)
        ApiResponses apiResponses = AnnotatedElementUtils.findMergedAnnotation(method, ApiResponses.class);
        if (apiResponses != null) {
            for (ApiResponse response : apiResponses.value()) {
                if (isSuccessResponse(response) && hasContentDefinition(response)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasContentDefinition(ApiResponse apiResponse) {
        if (apiResponse.content().length == 0) {
            return false;
        }
        for (io.swagger.v3.oas.annotations.media.Content content : apiResponse.content()) {
            if (content.schema().implementation() != Void.class) {
                return true;
            }
        }
        return false;
    }

    private boolean isSuccessResponse(ApiResponse apiResponse) {
        String code = apiResponse.responseCode();
        return code.isEmpty() || "200".equals(code) || "default".equals(code);
    }

    /**
     * Unwrap transparent wrapper types that do not affect response body structure.
     * Handles nested wrapping like {@code ResponseEntity<Optional<T>>}.
     */
    private ResolvableType unwrapTransparent(ResolvableType type) {
        Class<?> rawClass = type.resolve();
        if (rawClass == null) {
            return type;
        }

        if (ResponseEntity.class.isAssignableFrom(rawClass)
                || Optional.class.isAssignableFrom(rawClass)) {
            ResolvableType inner = type.getGeneric(0);
            if (inner != ResolvableType.NONE) {
                return unwrapTransparent(inner);
            }
        }
        return type;
    }

    /**
     * Check if the type tree contains any known generic wrapper
     * that needs ResolvableType-based resolution.
     */
    private boolean containsKnownGenericWrapper(ResolvableType type) {
        Class<?> rawClass = type.resolve();
        if (rawClass == null) {
            return false;
        }

        if (SimpliXApiResponse.class.isAssignableFrom(rawClass)
                || isPageType(rawClass)
                || Collection.class.isAssignableFrom(rawClass)
                || Map.class.isAssignableFrom(rawClass)) {
            return true;
        }

        for (ResolvableType generic : type.getGenerics()) {
            if (generic != ResolvableType.NONE && containsKnownGenericWrapper(generic)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recursively resolve a {@link ResolvableType} to an OpenAPI {@link Schema}.
     * Traverses the generic type chain, building accurate nested schemas
     * for known wrapper types and {@code $ref} pointers for DTO types.
     */
    Schema<?> resolveSchema(ResolvableType type) {
        Class<?> rawClass = type.resolve();
        if (rawClass == null || rawClass == Object.class) {
            return new ObjectSchema();
        }

        // Void/void has no schema representation; use empty object
        if (rawClass == void.class || rawClass == Void.class) {
            return new ObjectSchema();
        }

        // Transparent unwrap (safety net for nested calls)
        if (ResponseEntity.class.isAssignableFrom(rawClass)
                || Optional.class.isAssignableFrom(rawClass)) {
            ResolvableType inner = type.getGeneric(0);
            return inner != ResolvableType.NONE ? resolveSchema(inner) : new ObjectSchema();
        }

        // SimpliXApiResponse<T>
        if (SimpliXApiResponse.class.isAssignableFrom(rawClass)) {
            ResolvableType bodyType = type.getGeneric(0);
            Schema<?> bodySchema = resolveGenericOrFallback(bodyType);
            return buildApiResponseSchema(bodySchema);
        }

        // Page<T> (extends Slice, check first for totals)
        if (PAGE_CLASS != null && PAGE_CLASS.isAssignableFrom(rawClass)) {
            return buildPageSchema(type, true);
        }

        // Slice<T> (no totalElements/totalPages)
        if (SLICE_CLASS != null && SLICE_CLASS.isAssignableFrom(rawClass)) {
            return buildPageSchema(type, false);
        }

        // Collection<T> (List, Set, etc.)
        if (Collection.class.isAssignableFrom(rawClass)) {
            ResolvableType elementType = type.getGeneric(0);
            Schema<?> itemSchema = resolveGenericOrFallback(elementType);
            return new ArraySchema().items(itemSchema);
        }

        // Map<K, V>
        if (Map.class.isAssignableFrom(rawClass)) {
            ResolvableType valueType = type.getGeneric(1);
            Schema<?> valueSchema = resolveGenericOrFallback(valueType);
            MapSchema mapSchema = new MapSchema();
            mapSchema.additionalProperties(valueSchema);
            return mapSchema;
        }

        // Primitive / well-known types
        Schema<?> primitiveSchema = resolvePrimitiveSchema(rawClass);
        if (primitiveSchema != null) {
            return primitiveSchema;
        }

        // DTO - reference via $ref
        return new Schema<>().$ref("#/components/schemas/" + rawClass.getSimpleName());
    }

    private Schema<?> resolveGenericOrFallback(ResolvableType type) {
        if (type == ResolvableType.NONE || type.resolve() == null) {
            return new ObjectSchema();
        }
        return resolveSchema(type);
    }

    /**
     * Build SimpliXApiResponse wrapper schema matching the actual JSON structure.
     *
     * @param bodySchema schema for the {@code body} field
     */
    private Schema<?> buildApiResponseSchema(Schema<?> bodySchema) {
        StringSchema typeSchema = new StringSchema();
        typeSchema.addEnumItemObject("SUCCESS");
        typeSchema.addEnumItemObject("FAILURE");
        typeSchema.addEnumItemObject("ERROR");
        typeSchema.setExample("SUCCESS");

        ObjectSchema schema = new ObjectSchema();
        schema.setTitle("SimpliXApiResponse");
        schema.addProperty("type", typeSchema);
        schema.addProperty("message", new StringSchema());
        schema.addProperty("body", bodySchema);
        schema.addProperty("timestamp", new StringSchema().format("date-time"));
        schema.addProperty("errorCode", new StringSchema());
        schema.addProperty("errorDetail", new ObjectSchema());
        return schema;
    }


    /**
     * Build Spring Data Page/Slice schema matching Jackson serialization output.
     *
     * @param type          the Page/Slice ResolvableType
     * @param includeTotals true for Page (has totalElements/totalPages), false for Slice
     */
    private Schema<?> buildPageSchema(ResolvableType type, boolean includeTotals) {
        ResolvableType elementType = type.getGeneric(0);
        Schema<?> itemSchema = resolveGenericOrFallback(elementType);

        String title = includeTotals ? "Page" : "Slice";

        ObjectSchema sortSchema = new ObjectSchema();
        sortSchema.setTitle("Sort");
        sortSchema.addProperty("sorted", new BooleanSchema());
        sortSchema.addProperty("unsorted", new BooleanSchema());
        sortSchema.addProperty("empty", new BooleanSchema());

        ObjectSchema pageableSchema = new ObjectSchema();
        pageableSchema.setTitle("Pageable");
        pageableSchema.addProperty("pageNumber", new IntegerSchema());
        pageableSchema.addProperty("pageSize", new IntegerSchema());
        pageableSchema.addProperty("offset", new IntegerSchema().format("int64"));
        pageableSchema.addProperty("sort", sortSchema);
        pageableSchema.addProperty("paged", new BooleanSchema());
        pageableSchema.addProperty("unpaged", new BooleanSchema());

        ObjectSchema pageSchema = new ObjectSchema();
        pageSchema.setTitle(title);
        pageSchema.addProperty("content", new ArraySchema().items(itemSchema));
        pageSchema.addProperty("pageable", pageableSchema);
        if (includeTotals) {
            pageSchema.addProperty("totalElements", new IntegerSchema().format("int64"));
            pageSchema.addProperty("totalPages", new IntegerSchema());
        }
        pageSchema.addProperty("size", new IntegerSchema());
        pageSchema.addProperty("number", new IntegerSchema());
        pageSchema.addProperty("sort", sortSchema);
        pageSchema.addProperty("first", new BooleanSchema());
        pageSchema.addProperty("last", new BooleanSchema());
        pageSchema.addProperty("numberOfElements", new IntegerSchema());
        pageSchema.addProperty("empty", new BooleanSchema());

        return pageSchema;
    }

    private Schema<?> resolvePrimitiveSchema(Class<?> type) {
        if (type == String.class) return new StringSchema();
        if (type == Integer.class || type == int.class) return new IntegerSchema();
        if (type == Long.class || type == long.class) return new IntegerSchema().format("int64");
        if (type == Double.class || type == double.class) return new NumberSchema().format("double");
        if (type == Float.class || type == float.class) return new NumberSchema().format("float");
        if (type == Boolean.class || type == boolean.class) return new BooleanSchema();
        if (type == Byte.class || type == byte.class) return new StringSchema().format("byte");
        return null;
    }

    private boolean isPageType(Class<?> rawClass) {
        return (PAGE_CLASS != null && PAGE_CLASS.isAssignableFrom(rawClass))
                || (SLICE_CLASS != null && SLICE_CLASS.isAssignableFrom(rawClass));
    }

    private boolean isStreamingType(Class<?> rawClass) {
        String name = rawClass.getName();
        return "org.springframework.web.servlet.mvc.method.annotation.SseEmitter".equals(name)
                || "org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody".equals(name)
                || "org.springframework.core.io.Resource".equals(name);
    }

    private void setResponseSchema(Operation operation, Schema<?> schema) {
        if (operation.getResponses() == null) {
            return;
        }

        // Preserve existing examples from annotations like @SimpliXStandardApi
        io.swagger.v3.oas.models.responses.ApiResponse existingResponse =
                operation.getResponses().get("200");

        MediaType newMediaType = new MediaType().schema(schema);

        if (existingResponse != null && existingResponse.getContent() != null) {
            MediaType existingMediaType = existingResponse.getContent().get("application/json");
            if (existingMediaType != null) {
                if (existingMediaType.getExamples() != null) {
                    newMediaType.setExamples(existingMediaType.getExamples());
                }
                if (existingMediaType.getExample() != null) {
                    newMediaType.setExample(existingMediaType.getExample());
                }
            }
        }

        Content content = new Content().addMediaType("application/json", newMediaType);

        operation.getResponses()
                .computeIfAbsent("200", k -> new io.swagger.v3.oas.models.responses.ApiResponse())
                .content(content);
    }

    @Override
    public int getOrder() {
        // Run late to override springdoc's default type-erased schemas
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
