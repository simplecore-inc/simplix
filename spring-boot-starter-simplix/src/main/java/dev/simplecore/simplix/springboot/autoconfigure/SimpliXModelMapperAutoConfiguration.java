package dev.simplecore.simplix.springboot.autoconfigure;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfiguration
@ConditionalOnClass(ModelMapper.class)
public class SimpliXModelMapperAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimpliXModelMapperAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ModelMapper modelMapper() {
        log.info("Initializing SimpliX ModelMapper...");
        ModelMapper modelMapper = new ModelMapper();
        
        modelMapper.getConfiguration()
            .setMatchingStrategy(MatchingStrategies.STRICT)
            .setSkipNullEnabled(true)
            .setFieldMatchingEnabled(true)
            .setFieldAccessLevel(org.modelmapper.config.Configuration.AccessLevel.PRIVATE)
            .setPropertyCondition(context -> {
                if (context.getSource() == null) {
                    return false;
                }
                if (context.getSourceType().equals(Boolean.class)) {
                    return true;
                }
                return context.getSource() != null;
            });

        log.info("SimpliX ModelMapper initialized successfully");
        return modelMapper;
    }
} 