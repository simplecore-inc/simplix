/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.i18n;

import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;
import dev.simplecore.simplix.excel.properties.SimplixExcelProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelLabelsTest {

    private StaticMessageSource messages;

    /** An enum with labels in the message bundle. */
    enum Standing {
        ACTIVE, EXPIRED
    }

    /** An enum that carries its own label and has none in any bundle. */
    enum Channel implements SimpliXLabeledEnum {
        DIRECT;

        @Override
        public String getLabel() {
            return "Direct sale";
        }
    }

    /** An enum nothing labels at all. */
    enum Untranslated {
        SOMETHING
    }

    @BeforeEach
    void setUp() {
        messages = new StaticMessageSource();
        messages.addMessage("entities.Contract.status", Locale.ENGLISH, "Status");
        messages.addMessage("entities.Contract.status", Locale.KOREAN, "상태");
        messages.addMessage("enums.Standing.ACTIVE", Locale.ENGLISH, "Active");
        messages.addMessage("enums.Standing.ACTIVE", Locale.KOREAN, "사용 중");
        ExcelLabels.configure(messages, List.of(), new SimplixExcelProperties.FormatProperties());
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
        ExcelLabels.reset();
    }

    @Test
    void resolvesAPlaceholderColumnNameInTheCurrentLocale() {
        assertThat(ExcelLabels.columnName("{entities.Contract.status}")).isEqualTo("Status");

        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertThat(ExcelLabels.columnName("{entities.Contract.status}")).isEqualTo("상태");
    }

    @Test
    void leavesAColumnNameThatIsNotAPlaceholderAlone() {
        assertThat(ExcelLabels.columnName("Account Balance")).isEqualTo("Account Balance");
        assertThat(ExcelLabels.columnName("")).isEmpty();
        assertThat(ExcelLabels.columnName(null)).isEmpty();
    }

    @Test
    void readsAnUntranslatedPlaceholderAsItsKey() {
        assertThat(ExcelLabels.columnName("{entities.Contract.nothing}"))
                .isEqualTo("entities.Contract.nothing");
    }

    @Test
    void writesAnEnumAsItsLabel() {
        assertThat(ExcelLabels.enumLabel(Standing.ACTIVE)).isEqualTo("Active");

        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertThat(ExcelLabels.enumLabel(Standing.ACTIVE)).isEqualTo("사용 중");
    }

    @Test
    void fallsBackToTheEnumsOwnLabelWhenNoMessageHasIt() {
        assertThat(ExcelLabels.enumLabel(Channel.DIRECT)).isEqualTo("Direct sale");
    }

    @Test
    void writesAnEnumNothingLabelsAsItsOwnName() {
        assertThat(ExcelLabels.enumLabel(Standing.EXPIRED)).isEqualTo("EXPIRED");
        assertThat(ExcelLabels.enumLabel(Untranslated.SOMETHING)).isEqualTo("SOMETHING");
        assertThat(ExcelLabels.enumLabel(null)).isEmpty();
    }

    @Test
    void asksRegisteredResolversBeforeTheMessageSource() {
        ExcelLabelResolver first = (key, locale) ->
                "entities.Contract.status".equals(key) ? "From the resolver" : null;
        ExcelLabels.configure(messages, List.of(first), new SimplixExcelProperties.FormatProperties());

        assertThat(ExcelLabels.columnName("{entities.Contract.status}")).isEqualTo("From the resolver");
        // A key the resolver does not own still reaches the message source.
        assertThat(ExcelLabels.enumLabel(Standing.ACTIVE)).isEqualTo("Active");
    }

    @Test
    void treatsAResolverEchoingTheKeyAsAMiss() {
        ExcelLabelResolver echoing = (key, locale) -> key;
        ExcelLabels.configure(messages, List.of(echoing), new SimplixExcelProperties.FormatProperties());

        assertThat(ExcelLabels.columnName("{entities.Contract.status}")).isEqualTo("Status");
    }

    @Test
    void writesFlagsAsTheConfiguredWords() {
        assertThat(ExcelLabels.booleanLabel(true)).isEqualTo("Y");
        assertThat(ExcelLabels.booleanLabel(false)).isEqualTo("N");

        SimplixExcelProperties.FormatProperties format = new SimplixExcelProperties.FormatProperties();
        format.setBooleanTrueValue("{entities.Contract.status}");
        ExcelLabels.configure(messages, List.of(), format);
        assertThat(ExcelLabels.booleanLabel(true)).isEqualTo("Status");
    }

    @Test
    void survivesAContextWithNoMessagesAtAll() {
        ExcelLabels.configure(null, List.of(), null);

        assertThat(ExcelLabels.columnName("{entities.Contract.status}"))
                .isEqualTo("entities.Contract.status");
        assertThat(ExcelLabels.enumLabel(Standing.ACTIVE)).isEqualTo("ACTIVE");
        assertThat(ExcelLabels.booleanLabel(true)).isEqualTo("Y");
    }
}
