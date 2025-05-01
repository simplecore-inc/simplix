package dev.simplecore.simplix.auth.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "mybatis")
public class SimpliXMyBatisProperties {
    private boolean enabled = true;
    private String mapperLocations = "classpath*:mapper/**/*.xml";
    private String typeAliasesPackage;
    private String configLocation;
} 