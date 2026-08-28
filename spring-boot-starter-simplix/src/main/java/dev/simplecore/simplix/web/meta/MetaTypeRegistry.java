package dev.simplecore.simplix.web.meta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects DTO classes and enums discovered while mapping types. DTOs queue into a worklist so
 * the builder can process them iteratively — recursion-safe for self-referencing trees. Also
 * reports duplicate DTO simple names — the IR keys types by simple name, so two distinct classes
 * sharing one collide downstream.
 */
public class MetaTypeRegistry {

    private final Set<Class<?>> seen = new LinkedHashSet<>();
    private final Deque<Class<?>> pending = new ArrayDeque<>();
    private final Map<Class<?>, String> enums = new LinkedHashMap<>();
    private final Map<String, Class<?>> byName = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    /** Registers a DTO class (idempotent) and returns its IR type name. */
    public String register(Class<?> type) {
        String name = typeName(type);
        if (seen.add(type)) {
            pending.add(type);
            Class<?> incumbent = byName.putIfAbsent(name, type);
            if (incumbent != null) {
                warnings.add("Duplicate DTO simple name '" + name + "': "
                        + incumbent.getName() + " and " + type.getName()
                        + ". The IR keys types by simple name, so one overwrites the other."
                        + " Rename one of them.");
            }
        }
        return name;
    }

    /** Diagnostics collected while registering — currently duplicate simple names. */
    public List<String> warnings() { return warnings; }

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
