package dev.simplecore.simplix.core.aot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("SimpliXRuntimeHints")
class SimpliXRuntimeHintsTest {

    private SimpliXRuntimeHints registrar;
    private RuntimeHints hints;

    @BeforeEach
    void setUp() {
        registrar = new SimpliXRuntimeHints();
        hints = new RuntimeHints();
    }

    @Test
    @DisplayName("should register hints without throwing")
    void shouldRegisterHintsWithoutThrowing() {
        assertThatNoException().isThrownBy(() ->
            registrar.registerHints(hints, getClass().getClassLoader())
        );
    }

    @Test
    @DisplayName("should register SimpliXRepositoryFactoryBean for reflection")
    void shouldRegisterFactoryBean() {
        registrar.registerHints(hints, getClass().getClassLoader());

        TypeHint typeHint = hints.reflection().getTypeHint(
            dev.simplecore.simplix.core.tree.factory.SimpliXRepositoryFactoryBean.class
        );
        assertThat(typeHint).isNotNull();
        assertThat(typeHint.getMemberCategories())
            .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
    }

    @Test
    @DisplayName("should register SimpliXTreeRepositoryImpl for reflection")
    void shouldRegisterTreeRepositoryImpl() {
        registrar.registerHints(hints, getClass().getClassLoader());

        TypeHint typeHint = hints.reflection().getTypeHint(
            dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepositoryImpl.class
        );
        assertThat(typeHint).isNotNull();
    }

    @Test
    @DisplayName("should register TreeEntity interface for reflection")
    void shouldRegisterTreeEntity() {
        registrar.registerHints(hints, getClass().getClassLoader());

        TypeHint typeHint = hints.reflection().getTypeHint(
            dev.simplecore.simplix.core.tree.entity.TreeEntity.class
        );
        assertThat(typeHint).isNotNull();
        assertThat(typeHint.getMemberCategories())
            .contains(MemberCategory.INVOKE_PUBLIC_METHODS);
    }

    @Test
    @DisplayName("should register TreeEntityAttributes annotation for reflection")
    void shouldRegisterAnnotations() {
        registrar.registerHints(hints, getClass().getClassLoader());

        TypeHint typeHint = hints.reflection().getTypeHint(
            dev.simplecore.simplix.core.tree.annotation.TreeEntityAttributes.class
        );
        assertThat(typeHint).isNotNull();
    }
}
