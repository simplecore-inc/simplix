package dev.simplecore.simplix.demo.domain.common.auth.enums;

import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;

import lombok.Getter;

@Getter
public enum PermissionTargetType implements SimpliXLabeledEnum {
    ROLE("역할"),
    ORGANIZATION("조직"),
    USER("사용자");

    private final String label;

    PermissionTargetType(String label) {
        this.label = label;
    }
}