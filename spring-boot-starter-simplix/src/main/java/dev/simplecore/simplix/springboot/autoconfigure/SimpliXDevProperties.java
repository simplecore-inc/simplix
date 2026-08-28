package dev.simplecore.simplix.springboot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Settings for the framework's dev endpoints (meta + i18n). All off by default. */
@ConfigurationProperties(prefix = "simplix.dev")
public class SimpliXDevProperties {

    private final Meta meta = new Meta();
    private final I18n i18n = new I18n();

    /** Base packages scanned for labeled enums; empty = auto-configuration packages. */
    private List<String> basePackages = List.of();

    public Meta getMeta() { return meta; }
    public I18n getI18n() { return i18n; }
    public List<String> getBasePackages() { return basePackages; }
    public void setBasePackages(List<String> basePackages) { this.basePackages = basePackages; }

    public static class Meta {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class I18n {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
