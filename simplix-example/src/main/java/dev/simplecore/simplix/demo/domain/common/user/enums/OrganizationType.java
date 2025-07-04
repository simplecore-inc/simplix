package dev.simplecore.simplix.demo.domain.common.user.enums;

import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;
import dev.simplecore.simplix.core.annotation.I18nTitle;

import lombok.Getter;

@Getter
@I18nTitle({"ko=조직 유형", "en=Organization Type", "ja=組織タイプ"})
public enum OrganizationType implements SimpliXLabeledEnum {
    @I18nTitle({"ko=최상위 조직", "en=Root Organization", "ja=最上位組織"})
    ROOT("Root Organization"),

    @I18nTitle({"ko=부서", "en=Department", "ja=部署"})
    DEPARTMENT("Department"),
    
    @I18nTitle({"ko=그룹", "en=Group", "ja=グループ"})
    GROUP("Group");

    private final String label;

    OrganizationType(String label) {
        this.label = label;
    }
}

