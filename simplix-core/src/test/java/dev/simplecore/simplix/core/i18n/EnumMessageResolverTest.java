package dev.simplecore.simplix.core.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class EnumMessageResolverTest {

    @Test
    void resolvesFromClasspathBundlesPerLocale() {
        String ko = EnumMessageResolver.resolveMessage("enums.TestKind.ACTIVE", Locale.KOREAN);
        String en = EnumMessageResolver.resolveMessage("enums.TestKind.ACTIVE", Locale.ENGLISH);
        assertThat(ko).isEqualTo("활성");
        assertThat(en).isEqualTo("Active");
    }

    @Test
    void fallsBackToKeyWhenMissing() {
        String missing = EnumMessageResolver.resolveMessage("enums.TestKind.NOPE", Locale.ENGLISH);
        // AbstractMessageResolver.internalResolveMessage returns the key itself
        // when no bundle (locale-specific or ROOT) contains it.
        assertThat(missing).isEqualTo("enums.TestKind.NOPE");
    }
}
