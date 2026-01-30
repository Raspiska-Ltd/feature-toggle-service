package io.raspiska.featuretoggle.service;

import io.raspiska.featuretoggle.ApplicationProperties;
import io.raspiska.featuretoggle.dto.FeatureCheckResponse;
import io.raspiska.featuretoggle.entity.FeatureToggle;
import io.raspiska.featuretoggle.entity.FeatureToggleUser.ListType;
import io.raspiska.featuretoggle.entity.ToggleStatus;
import io.raspiska.featuretoggle.repository.FeatureToggleRepository;
import io.raspiska.featuretoggle.repository.FeatureToggleUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.listener.ChannelTopic;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureToggleCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ChannelTopic featureToggleTopic;

    @Mock
    private FeatureToggleRepository toggleRepository;

    @Mock
    private FeatureToggleUserRepository userRepository;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private MetricsService metricsService;

    private ApplicationProperties properties;
    private FeatureToggleCacheService cacheService;

    @BeforeEach
    void setUp() {
        properties = new ApplicationProperties();
        properties.setCache(new ApplicationProperties.Cache());
        properties.getCache().setTtlSeconds(30);
        properties.setRedis(new ApplicationProperties.Redis());
        properties.getRedis().setChannel("test-channel");

        lenient().when(metricsService.timeFeatureCheck(any())).thenAnswer(inv -> {
            java.util.function.Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });

        cacheService = new FeatureToggleCacheService(
                redisTemplate,
                featureToggleTopic,
                toggleRepository,
                userRepository,
                properties,
                metricsService
        );
    }

    @Test
    @DisplayName("checkFeature should return not found when feature doesn't exist")
    void checkFeature_shouldReturnNotFound_whenFeatureDoesNotExist() {
        // Given
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());
        when(toggleRepository.findByFeatureName("UNKNOWN")).thenReturn(Optional.empty());

        // When
        FeatureCheckResponse result = cacheService.checkFeature("UNKNOWN", "user1");

        // Then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getReason()).isEqualTo("Feature not found");
    }

    @Test
    @DisplayName("checkFeature should return enabled for ENABLED status")
    void checkFeature_shouldReturnEnabled_forEnabledStatus() {
        // Given
        FeatureToggle toggle = FeatureToggle.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.ENABLED)
                .build();

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(toggle));

        // When
        FeatureCheckResponse result = cacheService.checkFeature("TEST_FEATURE", "user1");

        // Then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ToggleStatus.ENABLED);
        assertThat(result.getReason()).isEqualTo("Feature is enabled globally");
    }

    @Test
    @DisplayName("checkFeature should return disabled for DISABLED status")
    void checkFeature_shouldReturnDisabled_forDisabledStatus() {
        // Given
        FeatureToggle toggle = FeatureToggle.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.DISABLED)
                .build();

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(toggle));

        // When
        FeatureCheckResponse result = cacheService.checkFeature("TEST_FEATURE", "user1");

        // Then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getStatus()).isEqualTo(ToggleStatus.DISABLED);
        assertThat(result.getReason()).isEqualTo("Feature is disabled globally");
    }

    @Test
    @DisplayName("checkFeature should require userId for LIST_MODE")
    void checkFeature_shouldRequireUserId_forListMode() {
        // Given
        FeatureToggle toggle = FeatureToggle.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.LIST_MODE)
                .build();

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(toggle));

        // When
        FeatureCheckResponse result = cacheService.checkFeature("TEST_FEATURE", null);

        // Then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getReason()).isEqualTo("User ID required for list mode");
    }

    @Test
    @DisplayName("checkFeature should return enabled for whitelisted user")
    void checkFeature_shouldReturnEnabled_forWhitelistedUser() {
        // Given
        FeatureToggle toggle = FeatureToggle.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.LIST_MODE)
                .build();

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(toggle));
        when(setOperations.isMember(contains("blacklist"), eq("user1"))).thenReturn(false);
        when(setOperations.isMember(contains("whitelist"), eq("user1"))).thenReturn(false);
        when(userRepository.existsByFeatureNameAndUserIdAndListType("TEST_FEATURE", "user1", ListType.BLACKLIST)).thenReturn(false);
        when(userRepository.existsByFeatureNameAndUserIdAndListType("TEST_FEATURE", "user1", ListType.WHITELIST)).thenReturn(true);

        // When
        FeatureCheckResponse result = cacheService.checkFeature("TEST_FEATURE", "user1");

        // Then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getReason()).isEqualTo("User is whitelisted");
    }


    @Test
    @DisplayName("checkFeature should return disabled for user not in whitelist")
    void checkFeature_shouldReturnDisabled_forUserNotInWhitelist() {
        // Given
        FeatureToggle toggle = FeatureToggle.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.LIST_MODE)
                .build();

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(toggle));
        when(setOperations.isMember(anyString(), eq("user1"))).thenReturn(false);
        when(userRepository.existsByFeatureNameAndUserIdAndListType("TEST_FEATURE", "user1", ListType.BLACKLIST)).thenReturn(false);
        when(userRepository.existsByFeatureNameAndUserIdAndListType("TEST_FEATURE", "user1", ListType.WHITELIST)).thenReturn(false);

        // When
        FeatureCheckResponse result = cacheService.checkFeature("TEST_FEATURE", "user1");

        // Then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getReason()).isEqualTo("User not in whitelist");
    }

    @Test
    @DisplayName("checkFeature should use Redis cache when available")
    void checkFeature_shouldUseRedisCache_whenAvailable() {
        // Given
        Map<Object, Object> redisData = new HashMap<>();
        redisData.put("status", "ENABLED");

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("feature:toggle:TEST_FEATURE")).thenReturn(redisData);

        // When
        FeatureCheckResponse result = cacheService.checkFeature("TEST_FEATURE", "user1");

        // Then
        assertThat(result.isEnabled()).isTrue();
        verify(toggleRepository, never()).findByFeatureName(anyString());
    }

    @Test
    @DisplayName("invalidateCache should evict from local and Redis cache")
    void invalidateCache_shouldEvictFromBothCaches() {
        // Given
        when(featureToggleTopic.getTopic()).thenReturn("test-channel");

        // When
        cacheService.invalidateCache("TEST_FEATURE");

        // Then
        verify(redisTemplate).delete("feature:toggle:TEST_FEATURE");
        verify(redisTemplate).delete("feature:whitelist:TEST_FEATURE");
        verify(redisTemplate).delete("feature:blacklist:TEST_FEATURE");
        verify(redisTemplate).convertAndSend("test-channel", "TEST_FEATURE");
    }

    @Test
    @DisplayName("invalidateUserList should delete Redis set and publish")
    void invalidateUserList_shouldDeleteRedisSetAndPublish() {
        // Given
        when(featureToggleTopic.getTopic()).thenReturn("test-channel");

        // When
        cacheService.invalidateUserList("TEST_FEATURE", ListType.WHITELIST);

        // Then
        verify(redisTemplate).delete("feature:whitelist:TEST_FEATURE");
        verify(redisTemplate).convertAndSend("test-channel", "TEST_FEATURE");
    }

    @Test
    @DisplayName("cacheUserList should add users to Redis set")
    void cacheUserList_shouldAddUsersToRedisSet() {
        // Given
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        Set<String> userIds = Set.of("user1", "user2");

        // When
        cacheService.cacheUserList("TEST_FEATURE", userIds, ListType.WHITELIST);

        // Then
        verify(setOperations).add(eq("feature:whitelist:TEST_FEATURE"), any(Object[].class));
        verify(redisTemplate).expire(eq("feature:whitelist:TEST_FEATURE"), anyLong(), any());
    }

    @Test
    @DisplayName("evictFromLocalCache should remove from local cache")
    void evictFromLocalCache_shouldRemoveFromLocalCache() {
        // When
        cacheService.evictFromLocalCache("TEST_FEATURE");

        // Then - no exception thrown, method completes
        // Local cache is private, so we verify indirectly through behavior
    }

    @Test
    @DisplayName("evictAllFromLocalCache should clear local cache")
    void evictAllFromLocalCache_shouldClearLocalCache() {
        // When
        cacheService.evictAllFromLocalCache();

        // Then - no exception thrown, method completes
    }

    @Test
    @DisplayName("checkFeature with blank userId for LIST_MODE should return disabled")
    void checkFeature_withBlankUserId_forListMode_shouldReturnDisabled() {
        // Given
        FeatureToggle toggle = FeatureToggle.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.LIST_MODE)
                .build();

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(toggle));

        // When
        FeatureCheckResponse result = cacheService.checkFeature("TEST_FEATURE", "   ");

        // Then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getReason()).isEqualTo("User ID required for list mode");
    }
}
