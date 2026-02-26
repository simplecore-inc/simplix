package dev.simplecore.simplix.web.config.openapi;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;

/**
 * OpenAPI customizer that ensures all DTO classes referenced in controller
 * return types are registered in OpenAPI components schemas.
 *
 * <p>When {@link GenericResponseSchemaCustomizer} builds schemas with
 * {@code $ref} pointers to DTO types, those types must exist in the
 * OpenAPI components. springdoc may not have discovered them due to
 * type erasure. This registrar scans all handler methods, collects
 * DTO classes from the generic type chain using {@link ResolvableType},
 * and registers them via {@link ModelConverters}.
 *
 * <p>Runs before other customizers ({@link EnumSchemaExtractor},
 * {@link NestedObjectSchemaExtractor}) to ensure schemas are available
 * for further processing.
 */
public class DtoSchemaAutoRegistrar implements OpenApiCustomizer, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DtoSchemaAutoRegistrar.class);

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

    private final RequestMappingHandlerMapping handlerMapping;

    public DtoSchemaAutoRegistrar(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void customise(OpenAPI openApi) {
        Set<Class<?>> dtoClasses = new HashSet<>();

        handlerMapping.getHandlerMethods().values().forEach(handlerMethod -> {
            ResolvableType returnType = ResolvableType.forMethodReturnType(handlerMethod.getMethod());
            collectDtoClasses(returnType, dtoClasses);
        });

        if (dtoClasses.isEmpty()) {
            return;
        }

        log.trace("Auto-registering {} DTO schemas from controller return types", dtoClasses.size());
        dtoClasses.forEach(clazz -> registerSchema(openApi, clazz));
    }

    private void registerSchema(OpenAPI openApi, Class<?> clazz) {
        if (openApi.getComponents() == null) {
            return;
        }

        String name = clazz.getSimpleName();

        if (openApi.getComponents().getSchemas() != null
                && openApi.getComponents().getSchemas().containsKey(name)) {
            return;
        }

        try {
            ResolvedSchema resolved = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(clazz));
            if (resolved == null) {
                return;
            }

            if (resolved.schema != null) {
                openApi.getComponents().addSchemas(name, resolved.schema);
                log.trace("Auto-registered DTO schema: {}", name);
            }
            if (resolved.referencedSchemas != null) {
                resolved.referencedSchemas.forEach((refName, refSchema) -> {
                    if (openApi.getComponents().getSchemas() == null
                            || !openApi.getComponents().getSchemas().containsKey(refName)) {
                        openApi.getComponents().addSchemas(refName, refSchema);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Failed to register schema for {}: {}", name, e.getMessage());
        }
    }

    /**
     * Recursively collect DTO classes from the ResolvableType tree.
     * Wrapper types are traversed; leaf types that are not JDK classes
     * are collected as DTO candidates.
     */
    private void collectDtoClasses(ResolvableType type, Set<Class<?>> result) {
        if (type == ResolvableType.NONE) {
            return;
        }

        Class<?> rawClass = type.resolve();
        if (rawClass == null) {
            return;
        }

        if (isWrapperType(rawClass)) {
            for (ResolvableType generic : type.getGenerics()) {
                collectDtoClasses(generic, result);
            }
        } else if (isDtoCandidate(rawClass)) {
            result.add(rawClass);
        }
    }

    private boolean isWrapperType(Class<?> rawClass) {
        return ResponseEntity.class.isAssignableFrom(rawClass)
                || Optional.class.isAssignableFrom(rawClass)
                || SimpliXApiResponse.class.isAssignableFrom(rawClass)
                || isPageType(rawClass)
                || Collection.class.isAssignableFrom(rawClass)
                || Map.class.isAssignableFrom(rawClass);
    }

    private boolean isDtoCandidate(Class<?> rawClass) {
        String name = rawClass.getName();
        return !name.startsWith("java.")
                && !name.startsWith("javax.")
                && !name.startsWith("jakarta.")
                && !name.startsWith("org.springframework.")
                && !rawClass.isPrimitive()
                && !rawClass.isEnum()
                && rawClass != void.class
                && rawClass != Void.class;
    }

    private boolean isPageType(Class<?> rawClass) {
        return (PAGE_CLASS != null && PAGE_CLASS.isAssignableFrom(rawClass))
                || (SLICE_CLASS != null && SLICE_CLASS.isAssignableFrom(rawClass));
    }

    @Override
    public int getOrder() {
        // Run before EnumSchemaExtractor and NestedObjectSchemaExtractor
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
