package dev.simplecore.simplix.demo;

import dev.simplecore.simplix.springboot.autoconfigure.SimpliXTreeRepositoryAutoConfiguration.EnableSimplixTreeRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableIntegration
@EnableSimplixTreeRepositories(basePackages = "dev.simplecore.simplix.demo.domain")
public class SimpliXExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimpliXExampleApplication.class, args);
    }
} 