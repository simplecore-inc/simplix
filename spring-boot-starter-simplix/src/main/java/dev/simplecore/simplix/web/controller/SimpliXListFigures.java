package dev.simplecore.simplix.web.controller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The figures a list of the entity a {@code SimpliXBaseController} serves can be summed up by,
 * published by SimpliX Meta as the entity's {@code statusCounts} and {@code aggregates}: the
 * fields {@code GET /status-counts?field=} accepts, and what {@code GET /aggregates} returns, in
 * its order. A client draws its cards from them instead of guessing a field the server refuses.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SimpliXListFigures {

    /** The fields the status counts count rows by. */
    String[] statusCounts() default {};

    /** The figures the aggregates return, in their order. */
    Figure[] aggregates() default {};

    /** One computed figure. */
    @interface Figure {

        /** The entity field. */
        String field();

        /** {@code sum}, {@code count}, {@code countDistinct}, {@code min} or {@code max}. */
        String op();
    }
}
