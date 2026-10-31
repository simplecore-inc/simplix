package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.core.i18n.EntityMessageResolver;
import dev.simplecore.simplix.excel.i18n.ExcelLabelResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.util.Locale;

/**
 * Lets an exported file's headers read the entity labels.
 *
 * <p>Entity labels ({@code entities.<Entity>.<field>}) are merged from
 * {@code messages/entities/**}, which the application {@code MessageSource} does not reach, so an
 * export whose columns name them wrote the keys as its header row. This resolver answers those keys
 * from the entity bundles, in the language the file is written in.
 */
@AutoConfiguration
@ConditionalOnClass(ExcelLabelResolver.class)
public class SimpliXExcelLabelsAutoConfiguration {

    /** The prefix every entity label key carries. */
    static final String ENTITY_KEY_PREFIX = "entities.";

    /**
     * Resolves {@code entities.*} keys from the entity bundles, and nothing else.
     *
     * @return the resolver the excel module consults before the message source
     */
    @Bean
    public ExcelLabelResolver simplixEntityExcelLabelResolver() {
        return SimpliXExcelLabelsAutoConfiguration::resolveEntityLabel;
    }

    static String resolveEntityLabel(String key, Locale locale) {
        if (key == null || !key.startsWith(ENTITY_KEY_PREFIX)) return null;
        String text = EntityMessageResolver.resolveMessage(key, locale);
        // The resolver answers an unknown key with the key itself; that is no label.
        return text == null || text.equals(key) ? null : text;
    }
}
