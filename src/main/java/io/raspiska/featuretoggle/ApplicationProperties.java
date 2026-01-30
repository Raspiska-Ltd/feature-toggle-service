package io.raspiska.featuretoggle;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "feature-toggle")
public class ApplicationProperties {

    private Cache cache = new Cache();
    private Redis redis = new Redis();

    @Getter
    @Setter
    public static class Cache {
        private long ttlSeconds = 30;
    }

    @Getter
    @Setter
    public static class Redis {
        private boolean enabled = true;
        private String channel = "feature-toggle-updates";
    }
}
