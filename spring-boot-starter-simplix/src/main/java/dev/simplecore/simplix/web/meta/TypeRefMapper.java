package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.web.meta.model.TypeRef;
import org.springframework.core.ResolvableType;

import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Maps a {@link ResolvableType} to a SimpliX Meta {@link TypeRef}. Container names are the
 * JAVA names — the CLI profile owns the TypeScript mapping.
 */
public class TypeRefMapper {

    private static final Set<Class<?>> INTEGRAL =
            Set.of(byte.class, short.class, int.class, long.class,
                   Byte.class, Short.class, Integer.class, Long.class, BigInteger.class);
    private static final Set<Class<?>> FRACTIONAL =
            Set.of(float.class, double.class, Float.class, Double.class, BigDecimal.class);
    private static final Set<String> BINARY_CLASS_NAMES = Set.of(
            "org.springframework.core.io.Resource",
            "org.springframework.core.io.InputStreamResource",
            "org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody");

    private final MetaTypeRegistry registry;

    public TypeRefMapper(MetaTypeRegistry registry) {
        this.registry = registry;
    }

    /** Returns null for void/Void — an operation with no response body. */
    public TypeRef map(ResolvableType resolvable) {
        if (resolvable.getType() instanceof TypeVariable<?> variable) {
            return TypeRef.param(variable.getName());
        }
        Class<?> raw = resolvable.resolve(Object.class);

        if (raw == void.class || raw == Void.class) return null;
        if (raw == Object.class) return TypeRef.unknown();
        if (CharSequence.class.isAssignableFrom(raw) || raw == UUID.class || raw == char.class || raw == Character.class) {
            return TypeRef.string();
        }
        if (raw == boolean.class || raw == Boolean.class) return TypeRef.bool();
        if (INTEGRAL.contains(raw)) return TypeRef.number(true);
        if (FRACTIONAL.contains(raw)) return TypeRef.number(false);
        if (raw == Instant.class || raw == OffsetDateTime.class || raw == ZonedDateTime.class
                || raw == LocalDateTime.class) {
            return TypeRef.instant();
        }
        if (raw == LocalDate.class) return TypeRef.date();
        if (raw == LocalTime.class) return TypeRef.time(null);
        if (raw == byte[].class || BINARY_CLASS_NAMES.contains(raw.getName())
                || isAssignableToName(raw, "org.springframework.core.io.Resource")) {
            return TypeRef.binary();
        }
        if (isAssignableToName(raw, "org.springframework.web.multipart.MultipartFile")) {
            return TypeRef.file();
        }
        if (raw.isEnum()) {
            return TypeRef.enumRef(registry.registerEnum(raw));
        }
        if (raw.getName().equals("org.springframework.http.ResponseEntity")) {
            return map(resolvable.getGeneric(0));
        }
        if (Map.class.isAssignableFrom(raw)) {
            TypeRef value = map(resolvable.getGeneric(1));
            return TypeRef.container("Map", value == null ? List.of() : List.of(value));
        }
        if (Collection.class.isAssignableFrom(raw) || raw.isArray()) {
            ResolvableType element = raw.isArray()
                    ? resolvable.getComponentType() : resolvable.getGeneric(0);
            TypeRef elementRef = map(element);
            return TypeRef.container("List", elementRef == null ? List.of() : List.of(elementRef));
        }
        if (raw.getName().equals("dev.simplecore.simplix.core.model.SimpliXApiResponse")) {
            TypeRef body = map(resolvable.getGeneric(0));
            return TypeRef.container("SimpliXApiResponse", body == null ? List.of() : List.of(body));
        }
        if (raw.getName().equals("org.springframework.data.domain.Page")
                || raw.getName().equals("org.springframework.data.domain.Slice")) {
            TypeRef element = map(resolvable.getGeneric(0));
            return TypeRef.container("Page", element == null ? List.of() : List.of(element));
        }
        // Anything else is a DTO: register it for the worklist and reference it by name.
        return TypeRef.ref(registry.register(raw));
    }

    private static boolean isAssignableToName(Class<?> type, String interfaceName) {
        if (type.getName().equals(interfaceName)) return true;
        for (Class<?> iface : type.getInterfaces()) {
            if (isAssignableToName(iface, interfaceName)) return true;
        }
        Class<?> parent = type.getSuperclass();
        return parent != null && isAssignableToName(parent, interfaceName);
    }
}
