package dev.simplecore.simplix.core.entity.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity whose rows map one record to another (a role to a permission, a member to a
 * team) rather than owning anything of their own.
 *
 * <p>SimpliX Meta lists, for every entity, the entities that hold it: an entity whose search DTO
 * filters by a reference to the record. A mapping row passes that test for both ends it joins,
 * yet a detail screen drawing a tab of raw mapping rows shows the reader nothing they can act on.
 * An entity carrying this annotation is therefore never listed as a holder.
 *
 * <p>The mark is declared rather than inferred. "Has two references" is also true of an owning
 * entity that refers to a category and a parent, and reading it as a mapping would drop a holder
 * the screen needs.
 *
 * <pre>{@code
 * @Entity
 * @MappingEntity
 * public class RolePermission extends BaseEntity<String> { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MappingEntity {
}
