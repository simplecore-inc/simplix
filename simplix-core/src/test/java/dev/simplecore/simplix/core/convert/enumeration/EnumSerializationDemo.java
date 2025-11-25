package dev.simplecore.simplix.core.convert.enumeration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;
import dev.simplecore.simplix.core.jackson.SimpliXEnumSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class EnumSerializationDemo {

    enum UserRole implements SimpliXLabeledEnum {
        ADMIN("관리자"),
        USER("사용자"),
        GUEST("게스트");

        private final String label;

        UserRole(String label) {
            this.label = label;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    enum Priority implements SimpliXLabeledEnum {
        HIGH("높음", 3),
        MEDIUM("중간", 2),
        LOW("낮음", 1);

        private final String label;
        private final int level;

        Priority(String label, int level) {
            this.label = label;
            this.level = level;
        }

        @Override
        public String getLabel() {
            return label;
        }

        public int getLevel() {
            return level;
        }
    }

    @Test
    public void demonstrateEnumSerialization() throws Exception {
        StandardEnumConverter converter = new StandardEnumConverter();

        System.out.println("=== Testing StandardEnumConverter.toMap() ===\n");

        // Test UserRole (SimpliXLabeledEnum with only getLabel)
        UserRole adminRole = UserRole.ADMIN;
        Map<String, Object> adminMap = converter.toMap(adminRole);
        System.out.println("UserRole.ADMIN toMap():");
        System.out.println("  " + adminMap);
        System.out.println("  Contains 'value': " + adminMap.containsKey("value"));
        System.out.println("  Contains 'label': " + adminMap.containsKey("label"));
        System.out.println("  Value: " + adminMap.get("value"));
        System.out.println("  Label: " + adminMap.get("label"));
        System.out.println();

        // Test Priority (SimpliXLabeledEnum with getLabel and getLevel)
        Priority highPriority = Priority.HIGH;
        Map<String, Object> priorityMap = converter.toMap(highPriority);
        System.out.println("Priority.HIGH toMap():");
        System.out.println("  " + priorityMap);
        System.out.println("  Contains 'value': " + priorityMap.containsKey("value"));
        System.out.println("  Contains 'label': " + priorityMap.containsKey("label"));
        System.out.println("  Contains 'level': " + priorityMap.containsKey("level"));
        System.out.println("  Value: " + priorityMap.get("value"));
        System.out.println("  Label: " + priorityMap.get("label"));
        System.out.println("  Level: " + priorityMap.get("level"));
        System.out.println();

        // Test JSON Serialization
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer((Class)Enum.class, new SimpliXEnumSerializer());
        objectMapper.registerModule(module);

        System.out.println("=== Testing SimpliXEnumSerializer ===\n");

        String adminJson = objectMapper.writeValueAsString(adminRole);
        System.out.println("UserRole.ADMIN JSON:");
        System.out.println("  " + adminJson);
        System.out.println();

        String priorityJson = objectMapper.writeValueAsString(highPriority);
        System.out.println("Priority.HIGH JSON:");
        System.out.println("  " + priorityJson);
        System.out.println();

        System.out.println("=== Test Complete ===");
    }
}
