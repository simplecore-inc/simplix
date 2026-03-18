package dev.simplecore.simplix.springboot.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationInfoRunner - displays application info on startup")
class ApplicationInfoRunnerTest {

    @Mock
    private Environment env;

    @Mock
    private ApplicationArguments args;

    @Test
    @DisplayName("Should create ApplicationInfoRunner with Environment")
    void createRunner() {
        ApplicationInfoRunner runner = new ApplicationInfoRunner(env);

        assertThat(runner).isNotNull();
    }

    @Test
    @DisplayName("Should run and print application info with default port and profiles")
    void runWithDefaults() throws Exception {
        when(env.getProperty("server.port", "8080")).thenReturn("8080");
        when(env.getActiveProfiles()).thenReturn(new String[]{});

        ApplicationInfoRunner runner = new ApplicationInfoRunner(env);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(baos));
        try {
            runner.run(args);
        } finally {
            System.setOut(originalOut);
        }

        String output = baos.toString();
        assertThat(output).contains("default");
        assertThat(output).contains("8080");
    }

    @Test
    @DisplayName("Should display active profiles when configured")
    void runWithActiveProfiles() throws Exception {
        when(env.getProperty("server.port", "8080")).thenReturn("9090");
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev", "local"});

        ApplicationInfoRunner runner = new ApplicationInfoRunner(env);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(baos));
        try {
            runner.run(args);
        } finally {
            System.setOut(originalOut);
        }

        String output = baos.toString();
        assertThat(output).contains("dev, local");
        assertThat(output).contains("9090");
    }

    @Test
    @DisplayName("Should display custom port when server.port is set")
    void runWithCustomPort() throws Exception {
        when(env.getProperty("server.port", "8080")).thenReturn("3000");
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ApplicationInfoRunner runner = new ApplicationInfoRunner(env);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(baos));
        try {
            runner.run(args);
        } finally {
            System.setOut(originalOut);
        }

        String output = baos.toString();
        assertThat(output).contains("prod");
        assertThat(output).contains("3000");
    }
}
