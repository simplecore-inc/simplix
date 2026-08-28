package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
