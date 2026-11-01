package dev.simplecore.simplix.web.meta.model;

/**
 * The order an entity's list opens in, read from {@code @SimpliXDefaultSort} on its controller.
 *
 * @param field     the search DTO field
 * @param direction {@code asc} or {@code desc}
 */
public record DefaultSortMeta(String field, String direction) {}
