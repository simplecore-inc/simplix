package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.core.jackson.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.*;
import java.util.List;
import java.util.TimeZone;

@Configuration
@AutoConfigureBefore(JacksonAutoConfiguration.class)
public class SimpliXJacksonAutoConfiguration implements WebMvcConfigurer {
    
    private static final String datetimeFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    
    private final Environment environment;
    
    public SimpliXJacksonAutoConfiguration(Environment environment) {
        this.environment = environment;
    }
    
    private ZoneId getZoneId() {
        // 1. Check timezone from JVM system property
        String userTimezone = System.getProperty("user.timezone");
        if (userTimezone != null && !userTimezone.isEmpty()) {
            try {
                return ZoneId.of(userTimezone);
            } catch (Exception e) {
                // If timezone format is invalid, proceed to next priority
            }
        }

        // 2. Check timezone from Spring configuration
        String springTimezone = environment.getProperty("spring.jackson.time-zone");
        if (springTimezone != null && !springTimezone.isEmpty()) {
            try {
                return ZoneId.of(springTimezone);
            } catch (Exception e) {
                // If timezone format is invalid, proceed to next priority
            }
        }

        // 3. Use system default timezone
        return ZoneId.systemDefault();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        ZoneId zoneId = getZoneId();

        TimeZone timeZone = TimeZone.getTimeZone(zoneId);
        objectMapper.setTimeZone(timeZone);
        objectMapper.setDateFormat(new java.text.SimpleDateFormat(datetimeFormat));
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
        dateTimeModule.addSerializer(Instant.class, new SimpliXDateTimeSerializer(zoneId));
        
        dateTimeModule.addDeserializer(LocalDateTime.class, new SimpliXDateTimeDeserializer<LocalDateTime>(zoneId, LocalDateTime.class));
        dateTimeModule.addDeserializer(LocalDate.class, new SimpliXDateTimeDeserializer<LocalDate>(zoneId, LocalDate.class));
        dateTimeModule.addDeserializer(ZonedDateTime.class, new SimpliXDateTimeDeserializer<ZonedDateTime>(zoneId, ZonedDateTime.class));
        dateTimeModule.addDeserializer(Instant.class, new SimpliXDateTimeDeserializer<Instant>(zoneId, Instant.class));

        objectMapper.registerModule(dateTimeModule);
        
        SimpleModule booleanModule = new SimpleModule();
        booleanModule.addSerializer(Boolean.class, new SimpliXBooleanSerializer());
        booleanModule.addDeserializer(Boolean.class, new SimpliXBooleanDeserializer());
        objectMapper.registerModule(booleanModule);
        
        SimpleModule enumModule = new SimpleModule();
        enumModule.addSerializer((Class)Enum.class, new SimpliXEnumSerializer());
        objectMapper.registerModule(enumModule);

        return objectMapper;
    }


    @Bean
    @Primary
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        converter.setPrettyPrint(true);
        return converter;
    }

    @Override
    public void configureMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) {
        converters.add(0, new ByteArrayHttpMessageConverter());
        converters.add(1, new StringHttpMessageConverter());
        converters.add(2, mappingJackson2HttpMessageConverter(objectMapper()));
    }
}