package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One relation from a record to the records that hold it: the {@code entity} whose search DTO
 * carries {@code field} referencing the owner, the {@code operators} that field accepts, and the
 * paged {@code searchOperation} that lists the holders when filtered by the owner's id.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HolderMeta(String entity, String field, List<String> operators, String searchOperation) {}
