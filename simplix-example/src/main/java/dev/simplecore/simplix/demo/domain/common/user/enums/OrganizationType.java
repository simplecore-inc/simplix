package dev.simplecore.simplix.demo.domain.common.user.enums;

import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;
import lombok.Getter;

@Getter
public enum OrganizationType implements SimpliXLabeledEnum {
    ROOT("Root Organization"),

    DEPARTMENT("Department"),
    
    GROUP("Group");

    private final String label;

    OrganizationType(String label) {
        this.label = label;
    }
}

