package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Discriminated type reference. {@code kind} selects the shape; members not used by a
 * kind stay {@code null} and vanish from JSON via NON_NULL.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TypeRef(
        String kind,
        String name,
        Boolean integral,
        String pattern,
        List<TypeRef> args,
        String of,
        List<String> fields) {

    public static TypeRef string() { return bare("string"); }
    public static TypeRef bool() { return bare("boolean"); }
    public static TypeRef unknown() { return bare("unknown"); }
    public static TypeRef instant() { return bare("instant"); }
    public static TypeRef date() { return bare("date"); }
    public static TypeRef file() { return bare("file"); }
    public static TypeRef binary() { return bare("binary"); }

    public static TypeRef number(boolean integral) {
        return new TypeRef("number", null, integral, null, null, null, null);
    }

    public static TypeRef time(String pattern) {
        return new TypeRef("time", null, null, pattern, null, null, null);
    }

    public static TypeRef enumRef(String name) {
        return new TypeRef("enum", name, null, null, null, null, null);
    }

    public static TypeRef ref(String name) {
        return new TypeRef("ref", name, null, null, null, null, null);
    }

    public static TypeRef ref(String name, List<TypeRef> args) {
        return new TypeRef("ref", name, null, null, args, null, null);
    }

    public static TypeRef param(String name) {
        return new TypeRef("param", name, null, null, null, null, null);
    }

    public static TypeRef container(String name, List<TypeRef> args) {
        return new TypeRef("container", name, null, null, args, null, null);
    }

    public static TypeRef pick(String of, List<String> fields) {
        return new TypeRef("pick", null, null, null, null, of, fields);
    }

    private static TypeRef bare(String kind) {
        return new TypeRef(kind, null, null, null, null, null, null);
    }
}
