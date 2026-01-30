package io.raspiska.featuretoggle.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "feature-toggle.client")
public class FeatureToggleClientProperties {

    private String serviceUrl = "http://localhost:8090";
    private Cache cache = new Cache();
    private Redis redis = new Redis();
    private Map<String, DefaultBehavior> defaults = new HashMap<>();
    private DefaultBehavior globalDefault = DefaultBehavior.DISABLED;

    @Getter
    @Setter
    public static class Cache {
        private long ttlSeconds = 30;
        private int maxSize = 1000;
    }

    @Getter
    @Setter
    public static class Redis {
        private String channel = "feature-toggle-updates";
        private boolean enabled = true;
        /**
         * When true, client reads directly from Redis instead of making HTTP calls.
         * Requires the client to have access to the same Redis instance as the service.
         * This eliminates HTTP overhead for feature checks.
         */
        private boolean directMode = false;
    }

    public enum DefaultBehavior {
        ENABLED,
        DISABLED
    }
}
