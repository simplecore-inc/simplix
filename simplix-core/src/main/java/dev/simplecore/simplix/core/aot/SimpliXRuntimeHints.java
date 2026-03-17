package dev.simplecore.simplix.core.aot;

import dev.simplecore.simplix.core.entity.SoftDeletable;
import dev.simplecore.simplix.core.tree.annotation.LookupColumn;
import dev.simplecore.simplix.core.tree.annotation.SortDirection;
import dev.simplecore.simplix.core.tree.annotation.TreeEntityAttributes;
import dev.simplecore.simplix.core.tree.entity.TreeEntity;
import dev.simplecore.simplix.core.tree.factory.SimpliXRepositoryFactoryBean;
import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepository;
import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepositoryImpl;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM Native Image runtime hints for SimpliX framework internals.
 *
 * <p>Registers reflection hints for SimpliX's tree repository system,
 * entity interfaces, and annotation types that are accessed via reflection
 * at runtime.
 */
public class SimpliXRuntimeHints implements RuntimeHintsRegistrar {

    private static final MemberCategory[] ALL_ACCESS = {
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS,
            MemberCategory.DECLARED_FIELDS
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerFactoryClasses(hints);
        registerTreeRepositoryClasses(hints);
        registerEntityInterfaces(hints);
        registerAnnotations(hints);
    }

    private void registerFactoryClasses(RuntimeHints hints) {
        hints.reflection().registerType(SimpliXRepositoryFactoryBean.class, ALL_ACCESS);

        // Private inner class — accessed via createRepositoryFactory()
        registerInnerClass(hints,
                "dev.simplecore.simplix.core.tree.factory.SimpliXRepositoryFactoryBean$SimpliXRepositoryFactory");
    }

    private void registerTreeRepositoryClasses(RuntimeHints hints) {
        hints.reflection().registerType(SimpliXTreeRepositoryImpl.class, ALL_ACCESS);
        hints.reflection().registerType(SimpliXTreeRepository.class,
                MemberCategory.INVOKE_PUBLIC_METHODS);
    }

    private void registerEntityInterfaces(RuntimeHints hints) {
        hints.reflection().registerType(TreeEntity.class,
                MemberCategory.INVOKE_PUBLIC_METHODS);
        hints.reflection().registerType(SoftDeletable.class,
                MemberCategory.INVOKE_PUBLIC_METHODS);
    }

    private void registerAnnotations(RuntimeHints hints) {
        hints.reflection().registerType(TreeEntityAttributes.class,
                MemberCategory.INVOKE_PUBLIC_METHODS);
        hints.reflection().registerType(LookupColumn.class,
                MemberCategory.INVOKE_PUBLIC_METHODS);
        hints.reflection().registerType(SortDirection.class,
                MemberCategory.INVOKE_PUBLIC_METHODS);
    }

    private void registerInnerClass(RuntimeHints hints, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            hints.reflection().registerType(clazz, ALL_ACCESS);
        } catch (ClassNotFoundException ignored) {
            // Inner class not accessible, skip
        }
    }
}
