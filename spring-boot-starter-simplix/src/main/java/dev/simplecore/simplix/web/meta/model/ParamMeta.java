package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A query or path parameter of an operation. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParamMeta(String name, TypeRef type, boolean required, String description) {}
