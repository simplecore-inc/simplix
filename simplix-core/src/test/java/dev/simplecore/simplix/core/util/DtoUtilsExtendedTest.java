package dev.simplecore.simplix.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DtoUtils - Extended Coverage")
class DtoUtilsExtendedTest {

    static class Entity {
        private String name;
        private int value;

        public Entity() {}
        public Entity(String name, int value) {
            this.name = name;
            this.value = value;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    static class Dto {
        private String name;
        private int value;

        public Dto() {}
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    @Nested
    @DisplayName("toDtoPage")
    class ToDtoPage {

        @Test
        @DisplayName("should convert entity page to DTO page")
        void shouldConvertPage() {
            Entity e1 = new Entity("A", 1);
            Entity e2 = new Entity("B", 2);
            Page<Entity> entityPage = new PageImpl<>(List.of(e1, e2), PageRequest.of(0, 10), 2);

            Page<Dto> result = DtoUtils.toDtoPage(entityPage, Dto.class);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getName()).isEqualTo("A");
            assertThat(result.getContent().get(1).getName()).isEqualTo("B");
            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }
}
