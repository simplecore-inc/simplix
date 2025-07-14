package dev.simplecore.simplix.demo.domain.common.auth.enums;

import dev.simplecore.simplix.core.annotation.I18nTitle;
import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;
import lombok.Getter;

@Getter
@I18nTitle({"ko=권한 대상 유형", "en=Permission Target Type", "ja=権限対象タイプ"})
public enum PermissionTargetType implements SimpliXLabeledEnum {
    @I18nTitle({"ko=역할", "en=Role", "ja=役割"})
    ROLE("Role"),

    @I18nTitle({"ko=조직", "en=Organization", "ja=組織"})
    ORGANIZATION("Organization"),
    
    @I18nTitle({"ko=사용자", "en=User", "ja=ユーザー"})
    USER("User");

    private final String label;

    PermissionTargetType(String label) {
        this.label = label;
    }
}