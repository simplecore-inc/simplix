package dev.simplecore.simplix.springboot.autoconfigure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.OffsetTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetTimeSerializer;
import dev.simplecore.simplix.core.jackson.*;
import dev.simplecore.simplix.springboot.web.timezone.TimezoneAwareInstantSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;

/**
 * Auto-configuration for Jackson ObjectMapper with SimpliX customizations.
 *
 * <p>Runs before {@link JacksonAutoConfiguration} so that this {@code @Primary ObjectMapper}
 * is registered first, causing Spring Boot's {@code @ConditionalOnMissingBean(ObjectMapper.class)}
 * check to skip the default {@code jacksonObjectMapper} bean.
 *
 * <p>Uses {@code @AutoConfiguration} (not {@code @Configuration}) to ensure
 * the {@code before} ordering is respected by Spring Boot 3.x's auto-configuration
 * processing pipeline.
 */
@AutoConfiguration(before = JacksonAutoConfiguration.class)
public class SimpliXJacksonAutoConfiguration implements WebMvcConfigurer {
    
    private static final String datetimeFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    
    private final Environment environment;
    
    public SimpliXJacksonAutoConfiguration(Environment environment) {
        this.environment = environment;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        ZoneId zoneId = ApplicationTimezoneResolver.resolve(environment);

        TimeZone timeZone = TimeZone.getTimeZone(zoneId);
        objectMapper.setTimeZone(timeZone);
        objectMapper.setDateFormat(new SimpleDateFormat(datetimeFormat));
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        SimpleModule dateTimeModule = new SimpleModule();
        dateTimeModule.addSerializer(LocalDateTime.class, new SimpliXDateTimeSerializer(zoneId));
        dateTimeModule.addSerializer(LocalDate.class, new SimpliXDateTimeSerializer(zoneId));
        dateTimeModule.addSerializer(ZonedDateTime.class, new SimpliXDateTimeSerializer(zoneId));
        dateTimeModule.addSerializer(OffsetDateTime.class, new SimpliXDateTimeSerializer(zoneId));

        String requestTimezoneFlag = environment.getProperty("simplix.date-time.request-timezone-enabled");
        boolean requestTimezoneEnabled = requestTimezoneFlag == null || Boolean.parseBoolean(requestTimezoneFlag);
        if (requestTimezoneEnabled) {
            // Per-request X-Timezone display; falls back to the app zone off-thread. Must live
            // on the primary mapper because the MVC converter is built from objectMapper() here,
            // not from Spring Boot's Jackson2ObjectMapperBuilder.
            dateTimeModule.addSerializer(Instant.class, new TimezoneAwareInstantSerializer(zoneId));
        } else {
            dateTimeModule.addSerializer(Instant.class, new SimpliXDateTimeSerializer(zoneId));
        }

        dateTimeModule.addDeserializer(LocalDateTime.class, new SimpliXDateTimeDeserializer<LocalDateTime>(zoneId, LocalDateTime.class));
        dateTimeModule.addDeserializer(LocalDate.class, new SimpliXDateTimeDeserializer<LocalDate>(zoneId, LocalDate.class));
        dateTimeModule.addDeserializer(ZonedDateTime.class, new SimpliXDateTimeDeserializer<ZonedDateTime>(zoneId, ZonedDateTime.class));
        dateTimeModule.addDeserializer(OffsetDateTime.class, new SimpliXDateTimeDeserializer<OffsetDateTime>(zoneId, OffsetDateTime.class));
        dateTimeModule.addDeserializer(Instant.class, new SimpliXDateTimeDeserializer<Instant>(zoneId, Instant.class));

        // Zone-less clock times have no timezone semantics, so they bypass the
        // zone-aware SimpliX handlers and use the plain ISO jsr310 codecs.
        dateTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ISO_LOCAL_TIME));
        dateTimeModule.addDeserializer(LocalTime.class, LocalTimeDeserializer.INSTANCE);
        dateTimeModule.addSerializer(OffsetTime.class, OffsetTimeSerializer.INSTANCE);
        dateTimeModule.addDeserializer(OffsetTime.class, OffsetTimeDeserializer.INSTANCE);

        objectMapper.registerModule(dateTimeModule);
        
        SimpleModule booleanModule = new SimpleModule();
        booleanModule.addSerializer(Boolean.class, new SimpliXBooleanSerializer());
        booleanModule.addDeserializer(Boolean.class, new SimpliXBooleanDeserializer());
        objectMapper.registerModule(booleanModule);
        
        SimpleModule enumModule = new SimpleModule();
        enumModule.addSerializer((Class)Enum.class, new SimpliXEnumSerializer());
        objectMapper.registerModule(enumModule);

        // Hibernate6Module for lazy loading support
        // Prevents LazyInitializationException during JSON serialization
        Hibernate6Module hibernate6Module = new Hibernate6Module();
        hibernate6Module.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        hibernate6Module.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
        objectMapper.registerModule(hibernate6Module);

        return objectMapper;
    }


    @Bean
    @Primary
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        converter.setPrettyPrint(true);
        return converter;
    }

    /**
     * Puts the SimpliX JSON converter in place of the JSON converters Spring registers, and
     * leaves every other default converter (byte array, string, resource, resource region,
     * form, XML when present) where Spring put it. The JSON converter goes after the byte
     * array and string converters, so a {@code String} or {@code byte[]} body is written as is
     * rather than as a JSON string.
     *
     * <p>This extends the default list rather than replacing it: an override of
     * {@code configureMessageConverters(List)} that adds anything makes Spring MVC skip its
     * defaults, and a {@code ResponseEntity<Resource>} then has no converter to write it.
     */
    @Override
    public void extendMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) {
        int position = -1;
        for (int i = converters.size() - 1; i >= 0; i--) {
            HttpMessageConverter<?> converter = converters.get(i);
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                converters.remove(i);
                position = i;
            }
        }
        for (int i = 0; i < converters.size(); i++) {
            HttpMessageConverter<?> converter = converters.get(i);
            if (converter instanceof ByteArrayHttpMessageConverter || converter instanceof StringHttpMessageConverter) {
                position = Math.max(position, i + 1);
            }
        }
        MappingJackson2HttpMessageConverter simplixConverter = mappingJackson2HttpMessageConverter(objectMapper());
        if (position < 0) {
            converters.add(simplixConverter);
        } else {
            converters.add(position, simplixConverter);
        }
    }
}
