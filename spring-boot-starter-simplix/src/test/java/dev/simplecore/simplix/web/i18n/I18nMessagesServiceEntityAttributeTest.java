package dev.simplecore.simplix.web.i18n;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class I18nMessagesServiceEntityAttributeTest {

    @Test
    void entityLevelKeysStayOutOfFieldLabels() {
        I18nMessagesService service = new I18nMessagesService(List.of("dev.simplecore.simplix.web.i18n.none"));
        EntityMessages probe = service.getEntityMessages().get("PluralProbe");

        assertThat(probe).isNotNull();
        assertThat(probe.getFields()).containsKey("probeName");
        assertThat(probe.getFields()).doesNotContainKey("_plural");
    }

    @Test
    void supportedLocalesAreDetectableWithoutAnInstance() {
        assertThat(I18nMessagesService.detectSupportedLocales()).contains("en");
    }
}
