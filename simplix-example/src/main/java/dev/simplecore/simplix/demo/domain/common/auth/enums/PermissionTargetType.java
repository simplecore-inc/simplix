package dev.simplecore.simplix.demo.domain.common.auth.enums;

import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;
import lombok.Getter;

@Getter
public enum PermissionTargetType implements SimpliXLabeledEnum {
    ROLE("Role"),

    ORGANIZATION("Organization"),
    
    USER("User");

    private final String label;

    PermissionTargetType(String label) {
        this.label = label;
    }
}