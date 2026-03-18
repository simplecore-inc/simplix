package dev.simplecore.simplix.web.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXService - service interface contract verification")
class SimpliXServiceTest {

    @Test
    @DisplayName("Should define findById method")
    void hasFindByIdMethod() {
        boolean hasMethod = hasMethod("findById", Object.class);

        assertThat(hasMethod).isTrue();
    }

    @Test
    @DisplayName("Should define findById with projection method")
    void hasFindByIdWithProjectionMethod() {
        boolean hasMethod = hasMethod("findById", Object.class, Class.class);

        assertThat(hasMethod).isTrue();
    }

    @Test
    @DisplayName("Should define findAllById method")
    void hasFindAllByIdMethod() {
        boolean hasMethod = hasMethod("findAllById", Iterable.class);

        assertThat(hasMethod).isTrue();
    }

    @Test
    @DisplayName("Should define findAll with pageable method")
    void hasFindAllPageableMethod() {
        boolean hasMethod = hasMethod("findAll", org.springframework.data.domain.Pageable.class);

        assertThat(hasMethod).isTrue();
    }

    @Test
    @DisplayName("Should define existsById method")
    void hasExistsByIdMethod() {
        boolean hasMethod = hasMethod("existsById", Object.class);

        assertThat(hasMethod).isTrue();
    }

    @Test
    @DisplayName("Should define deleteById method")
    void hasDeleteByIdMethod() {
        boolean hasMethod = hasMethod("deleteById", Object.class);

        assertThat(hasMethod).isTrue();
    }

    @Test
    @DisplayName("Should define delete method")
    void hasDeleteMethod() {
        boolean hasMethod = hasMethod("delete", Object.class);

        assertThat(hasMethod).isTrue();
    }

    @Test
    @DisplayName("Should define saveAll method")
    void hasSaveAllMethod() {
        boolean hasMethod = hasMethod("saveAll", Iterable.class);

        assertThat(hasMethod).isTrue();
    }

    @Test
    @DisplayName("Should define saveAndFlush method")
    void hasSaveAndFlushMethod() {
        boolean hasMethod = hasMethod("saveAndFlush", Object.class);

        assertThat(hasMethod).isTrue();
    }

    @Test
    @DisplayName("Should define hasOwnerPermission method")
    void hasOwnerPermissionMethod() {
        boolean hasMethod = hasMethod("hasOwnerPermission", String.class, Object.class, Object.class);

        assertThat(hasMethod).isTrue();
    }

    @Test
    @DisplayName("Should have default hasPermission method that delegates to hasOwnerPermission")
    void hasDefaultPermissionMethod() {
        List<Method> methods = Arrays.stream(SimpliXService.class.getMethods())
                .filter(m -> m.getName().equals("hasPermission"))
                .collect(Collectors.toList());

        assertThat(methods).isNotEmpty();
        assertThat(methods.get(0).isDefault()).isTrue();
    }

    private boolean hasMethod(String name, Class<?>... paramTypes) {
        return Arrays.stream(SimpliXService.class.getMethods())
                .anyMatch(m -> m.getName().equals(name) && m.getParameterCount() == paramTypes.length);
    }
}
