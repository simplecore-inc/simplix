package dev.simplecore.simplix.web.meta;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Collects DTO classes and enums discovered while mapping types. DTOs queue into a worklist so
 * the builder can process them iteratively — recursion-safe for self-referencing trees.
 */
public class MetaTypeRegistry {

    private final Set<Class<?>> seen = new LinkedHashSet<>();
    private final Deque<Class<?>> pending = new ArrayDeque<>();
    private final Map<Class<?>, String> enums = new LinkedHashMap<>();

    /** Registers a DTO class (idempotent) and returns its IR type name. */
    public String register(Class<?> type) {
        if (seen.add(type)) {
            pending.add(type);
        }
        return typeName(type);
    }

    /** Registers an enum (idempotent) and returns its IR name. */
    public String registerEnum(Class<?> enumType) {
        return enums.computeIfAbsent(enumType, MetaTypeRegistry::typeName);
    }

    public boolean hasPending() { return !pending.isEmpty(); }

    public Class<?> next() { return pending.remove(); }

    public Deque<Class<?>> pendingTypes() { return pending; }

    public Map<Class<?>, String> enums() { return enums; }

    /** Nested DTOs use the enclosing-stripped simple name: {@code AccessAreaDTOs$CreateDTO → CreateDTO}. */
    public static String typeName(Class<?> type) {
        return type.getSimpleName();
    }
}
