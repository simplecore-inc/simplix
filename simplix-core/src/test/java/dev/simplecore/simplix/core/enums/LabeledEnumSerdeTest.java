package dev.simplecore.simplix.core.enums;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LabeledEnumSerdeTest {

    enum TestKind implements LabeledEnum {
        ACTIVE, RETIRED
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesAsValueLabelObject() throws Exception {
        // value comes from name(); label resolves via EnumMessageResolver (falls back without bundles).
        // The wire contract is exactly these two properties, nothing more.
        JsonNode node = mapper.readTree(mapper.writeValueAsString(TestKind.ACTIVE));
        assertThat(node.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("value", "label");
        assertThat(node.get("value").asText()).isEqualTo("ACTIVE");
        assertThat(node.get("label").asText()).isNotBlank();
    }

    @Test
    void deserializesFromBareValueString() throws Exception {
        TestKind kind = mapper.readValue("\"RETIRED\"", TestKind.class);
        assertThat(kind).isEqualTo(TestKind.RETIRED);
    }

    @Test
    void deserializesFromValueLabelObject() throws Exception {
        TestKind kind = mapper.readValue("{\"value\":\"ACTIVE\",\"label\":\"whatever\"}", TestKind.class);
        assertThat(kind).isEqualTo(TestKind.ACTIVE);
    }
}
