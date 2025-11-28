package dev.simplecore.simplix.springboot.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.nio.charset.StandardCharsets;

@Component
@Order(Integer.MAX_VALUE)
public class ApplicationInfoRunner implements ApplicationRunner {
    private final Environment env;

    public ApplicationInfoRunner(Environment env) {
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String template = new String(FileCopyUtils.copyToByteArray(
            new ClassPathResource("config/application-info.txt").getInputStream()
        ), StandardCharsets.UTF_8);

        String yellow = "\u001B[93m";
        String blue = "\u001B[94m";
        String green = "\u001B[32m";
        String reset = "\u001B[0m";

        template = template.replace("${AnsiColor.BRIGHT_YELLOW}", yellow)
                          .replace("${AnsiColor.BRIGHT_BLUE}", blue)
                          .replace("${AnsiColor.BRIGHT_GREEN}", green)
                          .replace("${AnsiColor.GREEN}", green)
                          .replace("${AnsiColor.DEFAULT}", reset);

        String port = env.getProperty("server.port", "8080");
        String activeProfiles = String.join(", ", env.getActiveProfiles());
        if (activeProfiles.isEmpty()) {
            activeProfiles = "default";
        }

        System.out.printf((template) + "%n",
            activeProfiles,
            port, port, port, port
        );
    }
} 