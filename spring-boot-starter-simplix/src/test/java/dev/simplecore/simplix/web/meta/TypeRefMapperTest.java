package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.meta.model.TypeRef;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TypeRefMapperTest {

    static class SiteListDTO { String name; }

    enum PlainKind { A, B }

    static class Outer1 { static class Shared { String a; } }
    static class Outer2 { static class Shared { String b; } }

    @SuppressWarnings("unused")
    static class Holder {
        String text;
        boolean flag;
        int count;
        Double ratio;
        Instant createdAt;
        LocalDate day;
        LocalTime openAt;
        PlainKind kind;
        SiteListDTO site;
        List<SiteListDTO> sites;
        Map<String, Integer> counts;
        MultipartFile upload;
        SimpliXApiResponse<Page<SiteListDTO>> pageResponse;
        SimpliXApiResponse<Void> voidResponse;
    }

    private final MetaTypeRegistry registry = new MetaTypeRegistry();
    private final TypeRefMapper mapper = new TypeRefMapper(registry);

    private TypeRef map(String field) throws Exception {
        return mapper.map(ResolvableType.forField(Holder.class.getDeclaredField(field)));
    }

    @Test
    void primitivesAndStrings() throws Exception {
        assertThat(map("text")).isEqualTo(TypeRef.string());
        assertThat(map("flag")).isEqualTo(TypeRef.bool());
        assertThat(map("count")).isEqualTo(TypeRef.number(true));
        assertThat(map("ratio")).isEqualTo(TypeRef.number(false));
    }

    @Test
    void temporalKindsStayDistinct() throws Exception {
        assertThat(map("createdAt")).isEqualTo(TypeRef.instant());
        assertThat(map("day")).isEqualTo(TypeRef.date());
        assertThat(map("openAt")).isEqualTo(TypeRef.time(null));
    }

    @Test
    void enumAndDtoRegisterThemselves() throws Exception {
        assertThat(map("kind")).isEqualTo(TypeRef.enumRef("PlainKind"));
        assertThat(map("site")).isEqualTo(TypeRef.ref("SiteListDTO"));
        assertThat(registry.pendingTypes()).contains(SiteListDTO.class);
        assertThat(registry.enums()).containsKey(PlainKind.class);
    }

    @Test
    void containersNest() throws Exception {
        assertThat(map("sites"))
            .isEqualTo(TypeRef.container("List", List.of(TypeRef.ref("SiteListDTO"))));
        assertThat(map("counts"))
            .isEqualTo(TypeRef.container("Map", List.of(TypeRef.number(true))));
        assertThat(map("pageResponse"))
            .isEqualTo(TypeRef.container("SimpliXApiResponse",
                List.of(TypeRef.container("Page", List.of(TypeRef.ref("SiteListDTO"))))));
    }

    @Test
    void voidBodyCollapsesToNullArg() throws Exception {
        assertThat(map("voidResponse"))
            .isEqualTo(TypeRef.container("SimpliXApiResponse", List.of()));
    }

    @Test
    void multipartIsFile() throws Exception {
        assertThat(map("upload")).isEqualTo(TypeRef.file());
    }

    @Test
    void duplicateSimpleNamesAreReported() {
        MetaTypeRegistry reg = new MetaTypeRegistry();
        reg.register(Outer1.Shared.class);
        reg.register(Outer2.Shared.class);
        assertThat(reg.warnings()).hasSize(1);
        assertThat(reg.warnings().get(0))
                .contains("Shared")
                .contains(Outer1.Shared.class.getName())
                .contains(Outer2.Shared.class.getName());
        // both classes still queue — the registry keys by Class, not by name
        assertThat(reg.pendingTypes()).hasSize(2);
    }

    @Test
    void registeringTheSameClassTwiceDoesNotWarn() {
        MetaTypeRegistry reg = new MetaTypeRegistry();
        reg.register(SiteListDTO.class);
        reg.register(SiteListDTO.class);
        assertThat(reg.warnings()).isEmpty();
        assertThat(reg.pendingTypes()).hasSize(1);
    }
}
