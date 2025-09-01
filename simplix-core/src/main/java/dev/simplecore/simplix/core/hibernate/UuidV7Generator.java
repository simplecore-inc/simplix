package dev.simplecore.simplix.core.hibernate;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Hibernate ID Generator Type for UUID Version 7
 * 
 * This annotation creates UUID Version 7 identifiers for JPA entities
 * using the UUID Creator library for optimal performance and standard compliance.
 * 
 * Usage: @UuidV7Generator
 */
@IdGeneratorType(dev.simplecore.simplix.core.hibernate.UuidV7GeneratorImpl.class)
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface UuidV7Generator {
}
