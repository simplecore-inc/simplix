/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.i18n;

import dev.simplecore.simplix.excel.properties.SimplixExcelProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;

/**
 * Hands the application's own messages to {@link ExcelLabels} once the context is up.
 *
 * <p>A bean of its own rather than work in the auto-configuration's constructor: that constructor
 * runs while auto-configuration is still being assembled, and pulling the message source in there
 * would force it into existence ahead of the application's own customization of it.
 */
public class ExcelLabelsInitializer implements InitializingBean {

    private final ObjectProvider<MessageSource> messageSource;
    private final ObjectProvider<ExcelLabelResolver> resolvers;
    private final SimplixExcelProperties properties;

    /**
     * @param messageSource the application's messages, which a context need not have
     * @param resolvers whatever label resolvers the application registered, in bean order
     * @param properties the module's settings, whose boolean words flags are written with
     */
    public ExcelLabelsInitializer(ObjectProvider<MessageSource> messageSource,
                                  ObjectProvider<ExcelLabelResolver> resolvers,
                                  SimplixExcelProperties properties) {
        this.messageSource = messageSource;
        this.resolvers = resolvers;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        ExcelLabels.configure(
                messageSource.getIfAvailable(),
                resolvers.orderedStream().toList(),
                properties.getFormat());
    }
}
