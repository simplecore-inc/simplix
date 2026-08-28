package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IrJsonShapeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void typeRefContainerNestsArgs() throws Exception {
        TypeRef page = TypeRef.container("SimpliXApiResponse",
                List.of(TypeRef.container("Page", List.of(TypeRef.ref("SiteListDTO")))));
        String json = mapper.writeValueAsString(page);
        assertThat(json).isEqualTo(
            "{\"kind\":\"container\",\"name\":\"SimpliXApiResponse\",\"args\":[" +
            "{\"kind\":\"container\",\"name\":\"Page\",\"args\":[{\"kind\":\"ref\",\"name\":\"SiteListDTO\"}]}]}");
    }

    @Test
    void typeMetaSerializesExtendsKeyword() throws Exception {
        TypeMeta meta = new TypeMeta("a.b.C$UpdateDTO", "CreateDTO", List.of(), null, List.of());
        String json = mapper.writeValueAsString(meta);
        assertThat(json).contains("\"extends\":\"CreateDTO\"");
    }

    @Test
    void accessMetaShapes() throws Exception {
        assertThat(mapper.writeValueAsString(AccessMeta.permission("SYSTEM", "edit")))
            .isEqualTo("{\"kind\":\"permission\",\"group\":\"SYSTEM\",\"action\":\"edit\"}");
        assertThat(mapper.writeValueAsString(AccessMeta.publicAccess()))
            .isEqualTo("{\"kind\":\"public\"}");
    }

    @Test
    void numberCarriesIntegralFlagAndOthersOmitIt() throws Exception {
        assertThat(mapper.writeValueAsString(TypeRef.number(true)))
            .isEqualTo("{\"kind\":\"number\",\"integral\":true}");
        assertThat(mapper.writeValueAsString(TypeRef.string()))
            .isEqualTo("{\"kind\":\"string\"}");
    }

    @Test
    void fieldMetaSerializesWithConstraints() throws Exception {
        // required and nullable are unboxed primitives: they must appear even when false,
        // since the generator relies on these two keys always being present.
        FieldMeta field = new FieldMeta(
                "email",
                TypeRef.string(),
                false,
                true,
                null,
                null,
                null,
                List.of(ConstraintMeta.of("email"), ConstraintMeta.custom("UniqueEmail")),
                null);
        String json = mapper.writeValueAsString(field);
        assertThat(json).isEqualTo(
            "{\"name\":\"email\",\"type\":{\"kind\":\"string\"},\"required\":false,\"nullable\":true," +
            "\"constraints\":[{\"kind\":\"email\"},{\"kind\":\"custom\",\"name\":\"UniqueEmail\"}]}");
    }

    @Test
    void dtoMetaSerializesRoot() throws Exception {
        // typeParams/operations are empty Lists (present as "[]"); extends/description/extensions
        // are null (absent entirely) -- NON_NULL only drops null, never an empty collection.
        TypeMeta type = new TypeMeta("a.b.SiteDTO", null, List.of(), null, List.of());
        DtoMeta root = new DtoMeta(1, Map.of(), Map.of("SiteDTO", type), List.of(), null);
        String json = mapper.writeValueAsString(root);
        assertThat(json).isEqualTo(
            "{\"version\":1,\"enums\":{},\"types\":{\"SiteDTO\":{\"javaClass\":\"a.b.SiteDTO\"," +
            "\"typeParams\":[],\"fields\":[]}},\"operations\":[]}");
    }
}
