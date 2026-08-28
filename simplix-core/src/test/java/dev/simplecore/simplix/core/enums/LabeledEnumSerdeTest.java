package dev.simplecore.simplix.core.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabeledEnumSerdeTest {

    enum TestKind implements LabeledEnum {
        ACTIVE, RETIRED
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesAsValueLabelObject() throws Exception {
        String json = mapper.writeValueAsString(TestKind.ACTIVE);
        // value comes from name(); label resolves via EnumMessageResolver (falls back without bundles)
        assertThat(json).contains("\"value\":\"ACTIVE\"");
        assertThat(json).contains("\"label\":");
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
