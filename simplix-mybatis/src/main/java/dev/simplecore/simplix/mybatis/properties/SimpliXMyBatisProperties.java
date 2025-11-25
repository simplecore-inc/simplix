package dev.simplecore.simplix.mybatis.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "mybatis")
public class SimpliXMyBatisProperties {
    private boolean enabled = true;
    private String mapperLocations = "classpath*:mapper/**/*.xml";
    private String typeAliasesPackage;
    private String configLocation;
} 