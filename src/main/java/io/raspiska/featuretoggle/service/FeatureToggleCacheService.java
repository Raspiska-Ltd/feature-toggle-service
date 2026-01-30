package io.raspiska.featuretoggle.service;

import io.raspiska.featuretoggle.ApplicationProperties;
import io.raspiska.featuretoggle.dto.FeatureCheckResponse;
import io.raspiska.featuretoggle.entity.FeatureToggle;
import io.raspiska.featuretoggle.entity.FeatureToggleUser.ListType;
import io.raspiska.featuretoggle.entity.ToggleStatus;
import io.raspiska.featuretoggle.repository.FeatureToggleRepository;
import io.raspiska.featuretoggle.repository.FeatureToggleUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FeatureToggleCacheService {

    private static final String REDIS_KEY_PREFIX = "feature:toggle:";
    private static final String REDIS_WHITELIST_PREFIX = "feature:whitelist:";
    private static final String REDIS_BLACKLIST_PREFIX = "feature:blacklist:";

    private final Map<String, CachedToggle> localCache = new ConcurrentHashMap<>();
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic featureToggleTopic;
    private final FeatureToggleRepository toggleRepository;
    private final FeatureToggleUserRepository userRepository;
    private final ApplicationProperties properties;
    private final MetricsService metricsService;
    private final boolean redisEnabled;

    public FeatureToggleCacheService(@Nullable RedisTemplate<String, Object> redisTemplate,
                                      @Nullable ChannelTopic featureToggleTopic,
                                      FeatureToggleRepository toggleRepository,
                                      FeatureToggleUserRepository userRepository,
                                      ApplicationProperties properties,
                                      MetricsService metricsService) {
        this.redisTemplate = redisTemplate;
        this.featureToggleTopic = featureToggleTopic;
        this.toggleRepository = toggleRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.metricsService = metricsService;
        this.redisEnabled = redisTemplate != null && featureToggleTopic != null;
        
        if (!redisEnabled) {
            log.info("Redis is disabled, using local cache only");
        }
    }

    public FeatureCheckResponse checkFeature(String featureName, String userId) {
        return metricsService.timeFeatureCheck(() -> {
            CachedToggle cached = getFromCache(featureName);
            
            if (cached == null) {
                metricsService.recordFeatureCheck(featureName, false);
                return FeatureCheckResponse.builder()
                        .featureName(featureName)
                        .enabled(false)
                        .status(null)
                        .reason("Feature not found")
                        .build();
            }

            FeatureCheckResponse response = evaluateToggle(cached, userId);
            metricsService.recordFeatureCheck(featureName, response.isEnabled());
            return response;
        });
    }

    private FeatureCheckResponse evaluateToggle(CachedToggle cached, String userId) {
        ToggleStatus status = cached.status();
        String featureName = cached.featureName();

        return switch (status) {
            case ENABLED -> FeatureCheckResponse.builder()
                    .featureName(featureName)
                    .enabled(true)
                    .status(status)
                    .reason("Feature is enabled globally")
                    .build();

            case DISABLED -> FeatureCheckResponse.builder()
                    .featureName(featureName)
                    .enabled(false)
                    .status(status)
                    .reason("Feature is disabled globally")
                    .build();

            case LIST_MODE -> evaluateListMode(cached, userId);
        };
    }

    private FeatureCheckResponse evaluateListMode(CachedToggle cached, String userId) {
        String featureName = cached.featureName();

        if (userId == null || userId.isBlank()) {
            return FeatureCheckResponse.builder()
                    .featureName(featureName)
                    .enabled(false)
                    .status(ToggleStatus.LIST_MODE)
                    .reason("User ID required for list mode")
                    .build();
        }

        boolean isWhitelisted = isUserInList(featureName, userId, ListType.WHITELIST);
        boolean isBlacklisted = isUserInList(featureName, userId, ListType.BLACKLIST);

        if (isBlacklisted) {
            return FeatureCheckResponse.builder()
                    .featureName(featureName)
                    .enabled(false)
                    .status(ToggleStatus.LIST_MODE)
                    .reason("User is blacklisted")
                    .build();
        }

        if (isWhitelisted) {
            return FeatureCheckResponse.builder()
                    .featureName(featureName)
                    .enabled(true)
                    .status(ToggleStatus.LIST_MODE)
                    .reason("User is whitelisted")
                    .build();
        }

        return FeatureCheckResponse.builder()
                .featureName(featureName)
                .enabled(false)
                .status(ToggleStatus.LIST_MODE)
                .reason("User not in whitelist")
                .build();
    }

    private boolean isUserInList(String featureName, String userId, ListType listType) {
        if (redisEnabled) {
            String redisKey = (listType == ListType.WHITELIST ? REDIS_WHITELIST_PREFIX : REDIS_BLACKLIST_PREFIX) + featureName;
            try {
                Boolean isMember = redisTemplate.opsForSet().isMember(redisKey, userId);
                if (isMember != null && isMember) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Failed to check user in Redis: {}", featureName, e);
            }
        }
        return userRepository.existsByFeatureNameAndUserIdAndListType(featureName, userId, listType);
    }

    private CachedToggle getFromCache(String featureName) {
        CachedToggle localCached = localCache.get(featureName);
        if (localCached != null && !localCached.isExpired(properties.getCache().getTtlSeconds())) {
            metricsService.recordCacheHit();
            return localCached;
        }

        CachedToggle redisCached = getFromRedis(featureName);
        if (redisCached != null) {
            metricsService.recordCacheHit();
            localCache.put(featureName, redisCached);
            return redisCached;
        }

        metricsService.recordCacheMiss();
        CachedToggle dbToggle = loadFromDatabase(featureName);
        if (dbToggle != null) {
            saveToRedis(dbToggle);
            localCache.put(featureName, dbToggle);
        }
        return dbToggle;
    }

    @SuppressWarnings("unchecked")
    private CachedToggle getFromRedis(String featureName) {
        if (!redisEnabled) {
            return null;
        }
        try {
            Map<Object, Object> data = redisTemplate.opsForHash().entries(REDIS_KEY_PREFIX + featureName);
            if (data.isEmpty()) {
                return null;
            }
            return new CachedToggle(
                    featureName,
                    ToggleStatus.valueOf((String) data.get("status")),
                    System.currentTimeMillis()
            );
        } catch (Exception e) {
            log.warn("Failed to get toggle from Redis: {}", featureName, e);
            return null;
        }
    }

    private CachedToggle loadFromDatabase(String featureName) {
        Optional<FeatureToggle> toggle = toggleRepository.findByFeatureName(featureName);
        return toggle.map(t -> new CachedToggle(t.getFeatureName(), t.getStatus(), System.currentTimeMillis()))
                .orElse(null);
    }

    private void saveToRedis(CachedToggle toggle) {
        if (!redisEnabled) {
            return;
        }
        try {
            String key = REDIS_KEY_PREFIX + toggle.featureName();
            redisTemplate.opsForHash().put(key, "status", toggle.status().name());
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to save toggle to Redis: {}", toggle.featureName(), e);
        }
    }

    public void cacheUserList(String featureName, Set<String> userIds, ListType listType) {
        if (!redisEnabled) {
            return;
        }
        String redisKey = (listType == ListType.WHITELIST ? REDIS_WHITELIST_PREFIX : REDIS_BLACKLIST_PREFIX) + featureName;
        try {
            if (!userIds.isEmpty()) {
                redisTemplate.opsForSet().add(redisKey, userIds.toArray());
                redisTemplate.expire(redisKey, 1, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("Failed to cache user list for feature: {}", featureName, e);
        }
    }

    public void invalidateCache(String featureName) {
        evictFromLocalCache(featureName);
        evictFromRedis(featureName);
        publishInvalidation(featureName);
    }

    public void invalidateUserList(String featureName, ListType listType) {
        if (redisEnabled) {
            String redisKey = (listType == ListType.WHITELIST ? REDIS_WHITELIST_PREFIX : REDIS_BLACKLIST_PREFIX) + featureName;
            try {
                redisTemplate.delete(redisKey);
            } catch (Exception e) {
                log.warn("Failed to invalidate user list cache: {}", featureName, e);
            }
        }
        publishInvalidation(featureName);
    }

    public void evictFromLocalCache(String featureName) {
        localCache.remove(featureName);
        log.debug("Evicted from local cache: {}", featureName);
    }

    public void evictAllFromLocalCache() {
        localCache.clear();
        log.info("Cleared all local cache");
    }

    private void evictFromRedis(String featureName) {
        if (!redisEnabled) {
            return;
        }
        try {
            redisTemplate.delete(REDIS_KEY_PREFIX + featureName);
            redisTemplate.delete(REDIS_WHITELIST_PREFIX + featureName);
            redisTemplate.delete(REDIS_BLACKLIST_PREFIX + featureName);
        } catch (Exception e) {
            log.warn("Failed to evict from Redis: {}", featureName, e);
        }
    }

    private void publishInvalidation(String featureName) {
        if (!redisEnabled) {
            return;
        }
        try {
            redisTemplate.convertAndSend(featureToggleTopic.getTopic(), featureName);
            log.debug("Published cache invalidation for: {}", featureName);
        } catch (Exception e) {
            log.warn("Failed to publish cache invalidation: {}", featureName, e);
        }
    }

    private record CachedToggle(String featureName, ToggleStatus status, long cachedAt) {
        boolean isExpired(long ttlSeconds) {
            return System.currentTimeMillis() - cachedAt > ttlSeconds * 1000;
        }
    }
}
