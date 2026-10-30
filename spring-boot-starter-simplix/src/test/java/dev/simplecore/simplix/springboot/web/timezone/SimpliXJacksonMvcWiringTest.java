package dev.simplecore.simplix.springboot.web.timezone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.springboot.autoconfigure.SimpliXJacksonAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that a response written by Spring MVC, with the converters Spring Boot assembles, goes
 * through the SimpliX mapper rather than a converter Spring Boot registers on its own.
 *
 * <p>{@link SimpliXJacksonTimezoneWireTest} hands the converters to a standalone MockMvc and so
 * cannot see which converter the real auto-configuration puts first.
 */
@DisplayName("SimpliXJacksonMvcWiringTest - MVC responses use the SimpliX mapper")
class SimpliXJacksonMvcWiringTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpliXJacksonAutoConfiguration.class,
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    DispatcherServletAutoConfiguration.class,
                    WebMvcAutoConfiguration.class))
            .withUserConfiguration(WiringController.class)
            .withPropertyValues("simplix.date-time.default-timezone=Asia/Seoul");

    @Test
    @DisplayName("Instant, LocalDate and null fields follow the SimpliX contract")
    void responseFollowsSimpliXMapper() {
        runner.run(context -> {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
            String body = mockMvc.perform(get("/wiring").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode json = new ObjectMapper().readTree(body);
            assertThat(json.get("instant").asText()).startsWith("2026-01-01T09:00:00").endsWith("+09:00");
            assertThat(json.get("localDate").asText()).isEqualTo("2026-03-04");
            assertThat(json.has("absent")).as("null fields are omitted").isFalse();
        });
    }

    @Configuration
    @RestController
    static class WiringController {
        @GetMapping("/wiring")
        Map<String, Object> wiring() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("instant", Instant.parse("2026-01-01T00:00:00Z"));
            body.put("localDate", LocalDate.of(2026, 3, 4));
            body.put("absent", null);
            return body;
        }
    }
}
