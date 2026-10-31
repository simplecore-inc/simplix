package dev.simplecore.simplix.web.mapping;

import org.modelmapper.Condition;
import org.modelmapper.ModelMapper;
import org.modelmapper.spi.MappingContext;

/**
 * Keeps a field a create request leaves out from overwriting the default its entity starts with.
 *
 * <p>A service creates an entity with {@code new Entity()} and copies the request onto it. The
 * entity's own defaults (an {@code @Builder.Default}, a field initializer) are in place before the
 * copy, and a mapper that copies absent values too replaces each of them with {@code null}. For a
 * NOT NULL column that is an insert the database refuses, and for any other column it silently
 * stores a value nobody chose. On a create request an absent field means "use the default", so the
 * mapping skips it.
 *
 * <p>Only a {@code *CreateDTO} source is treated this way. An update request is a full replacement,
 * so a {@code null} in it stays a way to clear a field. {@code *UpdateDTO} classes extend their
 * {@code *CreateDTO}; the check reads the class of the object being mapped, never the classes it
 * inherits from, so the subclass is not mistaken for its parent.
 *
 * <p>The suffix is the DTO role name the generator gives every create request. A request class
 * named otherwise is mapped as before.
 */
public final class CreateRequestNullSkipCondition implements Condition<Object, Object> {

    /** The simple-name suffix of the DTO role that creates an entity. */
    public static final String CREATE_REQUEST_SUFFIX = "CreateDTO";

    /** The property condition the mapper had before this one, which must hold as well. */
    private final Condition<Object, Object> previous;

    private CreateRequestNullSkipCondition(Condition<Object, Object> previous) {
        this.previous = previous;
    }

    /**
     * Adds this condition to the mapper, beside any property condition it already has.
     *
     * @param mapper the mapper services copy requests onto entities with
     */
    public static void install(ModelMapper mapper) {
        Condition<?, ?> existing = mapper.getConfiguration().getPropertyCondition();
        if (existing instanceof CreateRequestNullSkipCondition) {
            return;
        }
        @SuppressWarnings("unchecked")
        Condition<Object, Object> previous = (Condition<Object, Object>) existing;
        mapper.getConfiguration().setPropertyCondition(new CreateRequestNullSkipCondition(previous));
    }

    @Override
    public boolean applies(MappingContext<Object, Object> context) {
        if (previous != null && !previous.applies(context)) {
            return false;
        }
        if (context.getSource() != null) {
            return true;
        }
        return !isCreateRequest(rootSourceType(context));
    }

    /** The class of the object the whole mapping started from, not of the property being copied. */
    private static Class<?> rootSourceType(MappingContext<?, ?> context) {
        MappingContext<?, ?> root = context;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        return root.getSource() != null ? root.getSource().getClass() : root.getSourceType();
    }

    private static boolean isCreateRequest(Class<?> type) {
        return type != null && type.getSimpleName().endsWith(CREATE_REQUEST_SUFFIX);
    }
}
