package dev.simplecore.simplix.springboot.web.timezone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.timezone.TimezoneContext;
import dev.simplecore.simplix.springboot.autoconfigure.SimpliXJacksonAutoConfiguration;
import dev.simplecore.simplix.springboot.autoconfigure.SimpliXTimezoneWebAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wire-level acceptance test proving that per-request {@code X-Timezone} timezone
 * display reaches the MVC message converter built by
 * {@link SimpliXJacksonAutoConfiguration#configureMessageConverters(List)}, and that
 * {@code LocalDate}/{@code LocalTime} serialize per the REST contract.
 *
 * <p>Assertions parse the raw HTTP response body (not an injected {@code ObjectMapper}),
 * so they exercise the exact converter MVC uses — the {@code configureMessageConverters}
 * fresh-instance path.
 */
@DisplayName("SimpliXJacksonTimezoneWireTest - X-Timezone reaches the MVC mapper")
class SimpliXJacksonTimezoneWireTest {

    private static final String APP_ZONE = "Asia/Seoul";

    private final ObjectMapper reader = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("simplix.date-time.default-timezone", APP_ZONE);

        SimpliXJacksonAutoConfiguration jacksonConfig = new SimpliXJacksonAutoConfiguration(environment);
        SimpliXTimezoneWebAutoConfiguration timezoneConfig =
                new SimpliXTimezoneWebAutoConfiguration(ZoneId.of(APP_ZONE));

        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        jacksonConfig.configureMessageConverters(converters);

        mockMvc = MockMvcBuilders.standaloneSetup(new WireController())
                .setMessageConverters(converters.toArray(new HttpMessageConverter[0]))
                .addInterceptors(timezoneConfig.timezoneInterceptor())
                .build();
    }

    @AfterEach
    void tearDown() {
        TimezoneContext.clear();
    }

    @Test
    @DisplayName("No X-Timezone header renders Instant at the app zone (+09:00)")
    void instantDefaultsToAppZone() throws Exception {
        JsonNode body = getDto(null);

        assertThat(body.get("instant").asText()).endsWith("+09:00");
    }

    @Test
    @DisplayName("X-Timezone: America/New_York renders Instant at that zone (-05:00)")
    void instantHonorsRequestZone() throws Exception {
        JsonNode body = getDto("America/New_York");

        // 2026-03-04T00:00:00Z is standard time in New York (DST starts 2026-03-08).
        assertThat(body.get("instant").asText()).endsWith("-05:00");
    }

    @Test
    @DisplayName("X-Timezone: UTC renders Instant at zero offset (Z)")
    void instantHonorsUtcRequestZone() throws Exception {
        JsonNode body = getDto("UTC");

        assertThat(body.get("instant").asText()).endsWith("Z");
    }

    @Test
    @DisplayName("LocalDate serializes as bare yyyy-MM-dd")
    void localDateIsBare() throws Exception {
        JsonNode body = getDto(null);

        assertThat(body.get("localDate").asText()).isEqualTo("2026-03-04");
    }

    @Test
    @DisplayName("LocalTime serializes as HH:mm:ss with no date or offset")
    void localTimeIsWallClock() throws Exception {
        JsonNode body = getDto(null);

        String localTime = body.get("localTime").asText();
        assertThat(localTime).isEqualTo("10:30:00");
        assertThat(localTime).doesNotContain("T", "+", "Z");
    }

    @Test
    @DisplayName("Posting an ambiguous non-ISO date is rejected with 400")
    void ambiguousDateRejected() throws Exception {
        mockMvc.perform(post("/wire/date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"03-04-2026\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Posting an ISO date succeeds and round-trips as bare yyyy-MM-dd")
    void isoDateAccepted() throws Exception {
        String responseBody = mockMvc.perform(post("/wire/date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-03-04\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(reader.readTree(responseBody).get("date").asText()).isEqualTo("2026-03-04");
    }

    private JsonNode getDto(String timezoneHeader) throws Exception {
        var request = get("/wire/dto").accept(MediaType.APPLICATION_JSON);
        if (timezoneHeader != null) {
            request = request.header("X-Timezone", timezoneHeader);
        }
        String responseBody = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return reader.readTree(responseBody);
    }

    @RestController
    static class WireController {

        @GetMapping("/wire/dto")
        WireDto dto() {
            return new WireDto(
                    Instant.parse("2026-03-04T00:00:00Z"),
                    LocalDate.of(2026, 3, 4),
                    LocalTime.of(10, 30));
        }

        @PostMapping("/wire/date")
        DateHolder echo(@RequestBody DateHolder body) {
            return body;
        }
    }

    static class WireDto {
        public Instant instant;
        public LocalDate localDate;
        public LocalTime localTime;

        WireDto(Instant instant, LocalDate localDate, LocalTime localTime) {
            this.instant = instant;
            this.localDate = localDate;
            this.localTime = localTime;
        }
    }

    static class DateHolder {
        public LocalDate date;
    }
}
