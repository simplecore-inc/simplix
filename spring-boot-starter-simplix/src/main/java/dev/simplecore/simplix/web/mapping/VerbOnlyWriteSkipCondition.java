package dev.simplecore.simplix.web.mapping;

import dev.simplecore.simplix.core.annotation.VerbOnly;
import org.modelmapper.Condition;
import org.modelmapper.ModelMapper;
import org.modelmapper.spi.Mapping;
import org.modelmapper.spi.MappingContext;
import org.modelmapper.spi.PropertyInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.Set;

/**
 * Keeps a request copied onto an entity from writing a field marked {@link VerbOnly}.
 *
 * <p>A verb-only field moves only through a verb of its own (enable and disable, publish and
 * withdraw), which sets it through the entity's setter. A service creates or updates the record by
 * copying the request onto the entity, and whatever value the request carries for the field is
 * skipped: a create keeps the entity's own default, the value SimpliX Meta publishes as
 * {@code defaultValue}, and an update keeps the stored value. The request DTO may still carry the
 * field, so a client that echoes the record back is not refused.
 *
 * <p>Only a destination field declared on a JPA entity (or its mapped superclass) is guarded. A DTO
 * field may carry the annotation so Meta publishes the flag on a derived value; copying an entity
 * into such a DTO still writes it.
 */
public final class VerbOnlyWriteSkipCondition implements Condition<Object, Object> {

    /** The annotations that make a class a persistent record, read by name so JPA stays optional. */
    private static final Set<String> PERSISTENT_TYPES = Set.of(
            "jakarta.persistence.Entity", "jakarta.persistence.MappedSuperclass");

    /** The property condition the mapper had before this one, which must hold as well. */
    private final Condition<Object, Object> previous;

    private VerbOnlyWriteSkipCondition(Condition<Object, Object> previous) {
        this.previous = previous;
    }

    /**
     * Adds this condition to the mapper, beside any property condition it already has.
     *
     * @param mapper the mapper services copy requests onto entities with
     */
    public static void install(ModelMapper mapper) {
        Condition<?, ?> existing = mapper.getConfiguration().getPropertyCondition();
        if (existing instanceof VerbOnlyWriteSkipCondition) {
            return;
        }
        @SuppressWarnings("unchecked")
        Condition<Object, Object> previous = (Condition<Object, Object>) existing;
        mapper.getConfiguration().setPropertyCondition(new VerbOnlyWriteSkipCondition(previous));
    }

    @Override
    public boolean applies(MappingContext<Object, Object> context) {
        if (previous != null && !previous.applies(context)) {
            return false;
        }
        Mapping mapping = context.getMapping();
        if (mapping == null) {
            return true;
        }
        Field destination = destinationField(mapping.getLastDestinationProperty());
        return destination == null
                || !destination.isAnnotationPresent(VerbOnly.class)
                || !isPersistent(destination.getDeclaringClass());
    }

    /**
     * The field behind the destination property: the field itself when the mapper writes it
     * directly, or the field of the same name a setter writes.
     */
    private static Field destinationField(PropertyInfo property) {
        if (property == null) {
            return null;
        }
        Member member = property.getMember();
        if (member instanceof Field field) {
            return field;
        }
        if (member == null) {
            return null;
        }
        for (Class<?> type = member.getDeclaringClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                return type.getDeclaredField(property.getName());
            } catch (NoSuchFieldException absent) {
                // keep walking up the hierarchy
            }
        }
        return null;
    }

    private static boolean isPersistent(Class<?> type) {
        for (Annotation annotation : type.getAnnotations()) {
            if (PERSISTENT_TYPES.contains(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }
}
