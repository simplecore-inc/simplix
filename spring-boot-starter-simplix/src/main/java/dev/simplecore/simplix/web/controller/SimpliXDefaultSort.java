package dev.simplecore.simplix.web.controller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The order a list of the entity a {@code SimpliXBaseController} serves opens in, published by
 * SimpliX Meta as the entity's {@code defaultSort}.
 *
 * <p>The field must be one the entity's search DTO declares sortable, and never the entity's id:
 * SimpliX Meta refuses to build otherwise, since a client's first request would be refused or
 * ordered by an opaque key.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SimpliXDefaultSort {

    /** The search DTO field the list is ordered by. */
    String field();

    /** {@code asc} or {@code desc}. */
    String direction() default "desc";
}
