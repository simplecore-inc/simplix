package dev.simplecore.simplix.web.meta.model;

/**
 * One figure an entity's {@code GET /aggregates} returns, read from {@code @SimpliXListFigures}.
 *
 * @param field the entity field
 * @param op    {@code sum}, {@code count}, {@code countDistinct}, {@code min} or {@code max}
 */
public record AggregateMeta(String field, String op) {}
