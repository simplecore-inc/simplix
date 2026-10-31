package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.excel.i18n.ExcelLabelResolver;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SimpliXExcelLabelsAutoConfigurationTest {

    private final ExcelLabelResolver resolver =
            new SimpliXExcelLabelsAutoConfiguration().simplixEntityExcelLabelResolver();

    @Test
    void entityLabelKeyResolvesFromTheEntityBundles() {
        assertThat(resolver.resolve("entities.PluralProbe.probeName", Locale.ENGLISH)).isEqualTo("Probe name");
    }

    @Test
    void unknownEntityKeyIsLeftToTheNextResolver() {
        assertThat(resolver.resolve("entities.PluralProbe.noSuchField", Locale.ENGLISH)).isNull();
    }

    @Test
    void keyOutsideTheEntityBundlesIsNotOwned() {
        assertThat(resolver.resolve("error.biz.payment.required", Locale.ENGLISH)).isNull();
    }
}
