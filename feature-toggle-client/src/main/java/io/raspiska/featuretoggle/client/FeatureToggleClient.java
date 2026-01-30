package io.raspiska.featuretoggle.client;

import io.raspiska.featuretoggle.client.FeatureToggleClientProperties.DefaultBehavior;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class FeatureToggleClient implements MessageListener {

    private static final String REDIS_KEY_PREFIX = "feature:toggle:";
    private static final String REDIS_WHITELIST_PREFIX = "feature:whitelist:";
    private static final String REDIS_BLACKLIST_PREFIX = "feature:blacklist:";

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FeatureToggleClientProperties properties;
    private final Map<String, CachedResult> localCache = new ConcurrentHashMap<>();
    private final boolean directRedisMode;

    public FeatureToggleClient(@Nullable RestTemplate restTemplate,
                                @Nullable RedisTemplate<String, Object> redisTemplate,
                                FeatureToggleClientProperties properties) {
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.directRedisMode = properties.getRedis().isDirectMode() && redisTemplate != null;
        
        if (directRedisMode) {
            log.info("Feature toggle client running in DIRECT REDIS MODE - no HTTP calls will be made");
        } else {
            log.info("Feature toggle client running in HTTP MODE - calls will be made to {}", properties.getServiceUrl());
        }
    }

    public boolean isEnabled(String featureName) {
        return isEnabled(featureName, null);
    }

    public boolean isEnabled(String featureName, String userId) {
        return check(featureName, userId).isEnabled();
    }

    public void requireEnabled(String featureName) throws FeatureDisabledException {
        requireEnabled(featureName, null);
    }

    public void requireEnabled(String featureName, String userId) throws FeatureDisabledException {
        FeatureCheckResult result = check(featureName, userId);
        if (!result.isEnabled()) {
            throw new FeatureDisabledException(featureName, result.getReason());
        }
    }

    public FeatureCheckResult check(String featureName) {
        return check(featureName, null);
    }

    public FeatureCheckResult check(String featureName, String userId) {
        // 1. Check local cache first
        CachedResult cached = localCache.get(cacheKey(featureName, userId));
        if (cached != null && !cached.isExpired(properties.getCache().getTtlSeconds())) {
            return toResult(featureName, cached, true, false);
        }

        try {
            // 2. Fetch from Redis directly or via HTTP
            FeatureCheckResult result = directRedisMode 
                    ? fetchFromRedis(featureName, userId)
                    : fetchFromService(featureName, userId);
            
            localCache.put(cacheKey(featureName, userId), new CachedResult(
                    result.isEnabled(),
                    result.getStatus(),
                    result.getReason(),
                    System.currentTimeMillis()
            ));
            return result;
        } catch (Exception e) {
            log.warn("Failed to check feature toggle '{}', using default behavior", featureName, e);
            return getDefaultResult(featureName);
        }
    }

    /**
     * Fetch feature toggle state directly from Redis (same logic as service).
     * This eliminates HTTP overhead when client shares Redis with service.
     */
    private FeatureCheckResult fetchFromRedis(String featureName, String userId) {
        try {
            // Get toggle data from Redis hash
            Map<Object, Object> toggleData = redisTemplate.opsForHash().entries(REDIS_KEY_PREFIX + featureName);
            
            if (toggleData.isEmpty()) {
                // Feature not found in Redis - return default
                log.debug("Feature '{}' not found in Redis", featureName);
                return getDefaultResult(featureName);
            }

            String status = (String) toggleData.get("status");
            
            if ("ENABLED".equals(status)) {
                return FeatureCheckResult.builder()
                        .featureName(featureName)
                        .enabled(true)
                        .status(status)
                        .reason("Feature is enabled globally")
                        .fromCache(false)
                        .fromDefault(false)
                        .build();
            }
            
            if ("DISABLED".equals(status)) {
                return FeatureCheckResult.builder()
                        .featureName(featureName)
                        .enabled(false)
                        .status(status)
                        .reason("Feature is disabled globally")
                        .fromCache(false)
                        .fromDefault(false)
                        .build();
            }
            
            // LIST_MODE - check whitelist/blacklist
            if ("LIST_MODE".equals(status)) {
                if (userId == null || userId.isBlank()) {
                    return FeatureCheckResult.builder()
                            .featureName(featureName)
                            .enabled(false)
                            .status(status)
                            .reason("User ID required for list mode")
                            .fromCache(false)
                            .fromDefault(false)
                            .build();
                }
                
                // Check blacklist first (takes precedence)
                Boolean isBlacklisted = redisTemplate.opsForSet().isMember(REDIS_BLACKLIST_PREFIX + featureName, userId);
                if (Boolean.TRUE.equals(isBlacklisted)) {
                    return FeatureCheckResult.builder()
                            .featureName(featureName)
                            .enabled(false)
                            .status(status)
                            .reason("User is blacklisted")
                            .fromCache(false)
                            .fromDefault(false)
                            .build();
                }
                
                // Check whitelist
                Boolean isWhitelisted = redisTemplate.opsForSet().isMember(REDIS_WHITELIST_PREFIX + featureName, userId);
                if (Boolean.TRUE.equals(isWhitelisted)) {
                    return FeatureCheckResult.builder()
                            .featureName(featureName)
                            .enabled(true)
                            .status(status)
                            .reason("User is whitelisted")
                            .fromCache(false)
                            .fromDefault(false)
                            .build();
                }
                
                // Not in any list
                return FeatureCheckResult.builder()
                        .featureName(featureName)
                        .enabled(false)
                        .status(status)
                        .reason("User not in whitelist")
                        .fromCache(false)
                        .fromDefault(false)
                        .build();
            }
            
            // Unknown status - use default
            return getDefaultResult(featureName);
            
        } catch (Exception e) {
            log.warn("Failed to fetch from Redis for feature '{}', falling back to HTTP", featureName, e);
            // Fallback to HTTP if Redis fails
            if (restTemplate != null) {
                return fetchFromService(featureName, userId);
            }
            throw e;
        }
    }

    private FeatureCheckResult fetchFromService(String featureName, String userId) {
        String url = properties.getServiceUrl() + "/api/v1/toggles/" + featureName + "/check";
        if (userId != null && !userId.isBlank()) {
            url += "?userId=" + userId;
        }

        try {
            ResponseEntity<FeatureCheckResult> response = restTemplate.getForEntity(url, FeatureCheckResult.class);
            FeatureCheckResult result = response.getBody();
            if (result != null) {
                result.setFromCache(false);
                result.setFromDefault(false);
            }
            return result;
        } catch (RestClientException e) {
            throw new RuntimeException("Failed to fetch feature toggle from service", e);
        }
    }

    private FeatureCheckResult getDefaultResult(String featureName) {
        DefaultBehavior behavior = properties.getDefaults().getOrDefault(featureName, properties.getGlobalDefault());
        boolean enabled = behavior == DefaultBehavior.ENABLED;

        return FeatureCheckResult.builder()
                .featureName(featureName)
                .enabled(enabled)
                .status("DEFAULT")
                .reason("Using default behavior: " + behavior)
                .fromCache(false)
                .fromDefault(true)
                .build();
    }

    private FeatureCheckResult toResult(String featureName, CachedResult cached, boolean fromCache, boolean fromDefault) {
        return FeatureCheckResult.builder()
                .featureName(featureName)
                .enabled(cached.enabled)
                .status(cached.status)
                .reason(cached.reason)
                .fromCache(fromCache)
                .fromDefault(fromDefault)
                .build();
    }

    private String cacheKey(String featureName, String userId) {
        return userId != null ? featureName + ":" + userId : featureName;
    }

    public void evictCache(String featureName) {
        localCache.entrySet().removeIf(entry -> entry.getKey().startsWith(featureName));
        log.debug("Evicted cache for feature: {}", featureName);
    }

    public void evictAllCache() {
        localCache.clear();
        log.debug("Evicted all feature toggle cache");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String featureName = new String(message.getBody());
        log.debug("Received cache invalidation for feature: {}", featureName);

        if ("*".equals(featureName)) {
            evictAllCache();
        } else {
            evictCache(featureName);
        }
    }

    private static class CachedResult {
        final String featureName;
        final boolean enabled;
        final String status;
        final String reason;
        final long cachedAt;

        CachedResult(boolean enabled, String status, String reason, long cachedAt) {
            this.featureName = null;
            this.enabled = enabled;
            this.status = status;
            this.reason = reason;
            this.cachedAt = cachedAt;
        }

        boolean isExpired(long ttlSeconds) {
            return System.currentTimeMillis() - cachedAt > ttlSeconds * 1000;
        }
    }
}
