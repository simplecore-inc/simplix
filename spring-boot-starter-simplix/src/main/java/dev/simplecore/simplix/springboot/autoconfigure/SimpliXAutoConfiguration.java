package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.springboot.properties.SimpliXProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.Order;

@AutoConfiguration
@EnableConfigurationProperties(SimpliXProperties.class)
@ComponentScan(basePackages = {"dev.simplecore.simplix.web", "dev.simplecore.simplix.springboot"})
@Order(0)
public class SimpliXAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SimpliXAutoConfiguration.class);
    private static boolean initialized = false;

    public SimpliXAutoConfiguration() {
        if (!initialized) {
            log.info("\n" +
                    "+------------------------------------------------+\n" +
                    "|          SimpliX Framework Auto Configuration    |\n" +
                    "+------------------------------------------------+");
            initialized = true;
        }
    }
} 

