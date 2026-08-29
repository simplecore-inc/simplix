package dev.simplecore.simplix.web.meta;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Collects DTO classes and enums discovered while mapping types. DTOs queue into a worklist so
 * the builder can process them iteratively — recursion-safe for self-referencing trees. Refuses
 * a duplicate DTO simple name rather than letting one silently overwrite the other — SimpliX Meta keys
 * types by simple name, so two distinct classes sharing one would collide downstream.
 */
public class MetaTypeRegistry {

    private final Set<Class<?>> seen = new LinkedHashSet<>();
    private final Deque<Class<?>> pending = new ArrayDeque<>();
    private final Map<Class<?>, String> enums = new LinkedHashMap<>();
    private final Map<String, Class<?>> byName = new LinkedHashMap<>();

    /** Registers a DTO class (idempotent) and returns its SimpliX Meta type name. */
    public String register(Class<?> type) {
        String name = typeName(type);
        if (seen.add(type)) {
            pending.add(type);
            Class<?> incumbent = byName.putIfAbsent(name, type);
            if (incumbent != null) {
                throw new IllegalStateException(
                        "Duplicate DTO simple name '" + name + "': "
                        + incumbent.getName() + " and " + type.getName()
                        + ". SimpliX Meta keys types by simple name, so one would overwrite the other."
                        + " Rename one of them.");
            }
        }
        return name;
    }

    /** Registers an enum (idempotent) and returns its SimpliX Meta name. */
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
