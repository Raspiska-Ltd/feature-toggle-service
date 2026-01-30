package io.raspiska.featuretoggle.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureToggleClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    private FeatureToggleClientProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FeatureToggleClientProperties();
        properties.setServiceUrl("http://localhost:8090");
        properties.getCache().setTtlSeconds(30);
        properties.getRedis().setEnabled(true);
        properties.getRedis().setChannel("feature-toggle-updates");
    }

    @Nested
    @DisplayName("HTTP Mode Tests")
    class HttpModeTests {

        private FeatureToggleClient client;

        @BeforeEach
        void setUp() {
            properties.getRedis().setDirectMode(false);
            client = new FeatureToggleClient(restTemplate, redisTemplate, properties);
        }

        @Test
        @DisplayName("isEnabled should return true when service returns enabled")
        void isEnabled_shouldReturnTrue_whenServiceReturnsEnabled() {
            // Given
            FeatureCheckResult serviceResult = FeatureCheckResult.builder()
                    .featureName("TEST_FEATURE")
                    .enabled(true)
                    .status("ENABLED")
                    .reason("Feature is enabled globally")
                    .build();
            when(restTemplate.getForEntity(anyString(), eq(FeatureCheckResult.class)))
                    .thenReturn(ResponseEntity.ok(serviceResult));

            // When
            boolean result = client.isEnabled("TEST_FEATURE");

            // Then
            assertThat(result).isTrue();
            verify(restTemplate).getForEntity(contains("/api/v1/toggles/TEST_FEATURE/check"), eq(FeatureCheckResult.class));
        }

        @Test
        @DisplayName("isEnabled should return false when service returns disabled")
        void isEnabled_shouldReturnFalse_whenServiceReturnsDisabled() {
            // Given
            FeatureCheckResult serviceResult = FeatureCheckResult.builder()
                    .featureName("TEST_FEATURE")
                    .enabled(false)
                    .status("DISABLED")
                    .reason("Feature is disabled globally")
                    .build();
            when(restTemplate.getForEntity(anyString(), eq(FeatureCheckResult.class)))
                    .thenReturn(ResponseEntity.ok(serviceResult));

            // When
            boolean result = client.isEnabled("TEST_FEATURE");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isEnabled should include userId in request")
        void isEnabled_shouldIncludeUserId_inRequest() {
            // Given
            FeatureCheckResult serviceResult = FeatureCheckResult.builder()
                    .featureName("TEST_FEATURE")
                    .enabled(true)
                    .status("LIST_MODE")
                    .reason("User is whitelisted")
                    .build();
            when(restTemplate.getForEntity(anyString(), eq(FeatureCheckResult.class)))
                    .thenReturn(ResponseEntity.ok(serviceResult));

            // When
            client.isEnabled("TEST_FEATURE", "user123");

            // Then
            verify(restTemplate).getForEntity(contains("userId=user123"), eq(FeatureCheckResult.class));
        }

        @Test
        @DisplayName("isEnabled should return default when service fails")
        void isEnabled_shouldReturnDefault_whenServiceFails() {
            // Given
            properties.setGlobalDefault(FeatureToggleClientProperties.DefaultBehavior.DISABLED);
            when(restTemplate.getForEntity(anyString(), eq(FeatureCheckResult.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            // When
            boolean result = client.isEnabled("TEST_FEATURE");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isEnabled should use feature-specific default when configured")
        void isEnabled_shouldUseFeatureDefault_whenConfigured() {
            // Given
            properties.getDefaults().put("WITHDRAW", FeatureToggleClientProperties.DefaultBehavior.DISABLED);
            properties.getDefaults().put("DEPOSIT", FeatureToggleClientProperties.DefaultBehavior.ENABLED);
            when(restTemplate.getForEntity(anyString(), eq(FeatureCheckResult.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            // When/Then
            assertThat(client.isEnabled("WITHDRAW")).isFalse();
            assertThat(client.isEnabled("DEPOSIT")).isTrue();
        }

        @Test
        @DisplayName("requireEnabled should throw exception when disabled")
        void requireEnabled_shouldThrowException_whenDisabled() {
            // Given
            FeatureCheckResult serviceResult = FeatureCheckResult.builder()
                    .featureName("TEST_FEATURE")
                    .enabled(false)
                    .status("DISABLED")
                    .reason("Feature is disabled globally")
                    .build();
            when(restTemplate.getForEntity(anyString(), eq(FeatureCheckResult.class)))
                    .thenReturn(ResponseEntity.ok(serviceResult));

            // When/Then
            assertThatThrownBy(() -> client.requireEnabled("TEST_FEATURE"))
                    .isInstanceOf(FeatureDisabledException.class)
                    .hasMessageContaining("TEST_FEATURE");
        }

        @Test
        @DisplayName("check should return result with fromCache=true on second call")
        void check_shouldReturnCachedResult_onSecondCall() {
            // Given
            FeatureCheckResult serviceResult = FeatureCheckResult.builder()
                    .featureName("TEST_FEATURE")
                    .enabled(true)
                    .status("ENABLED")
                    .reason("Feature is enabled globally")
                    .build();
            when(restTemplate.getForEntity(anyString(), eq(FeatureCheckResult.class)))
                    .thenReturn(ResponseEntity.ok(serviceResult));

            // When
            FeatureCheckResult firstCall = client.check("TEST_FEATURE");
            FeatureCheckResult secondCall = client.check("TEST_FEATURE");

            // Then
            assertThat(firstCall.isFromCache()).isFalse();
            assertThat(secondCall.isFromCache()).isTrue();
            verify(restTemplate, times(1)).getForEntity(anyString(), eq(FeatureCheckResult.class));
        }

        @Test
        @DisplayName("evictCache should clear cache for feature")
        void evictCache_shouldClearCacheForFeature() {
            // Given
            FeatureCheckResult serviceResult = FeatureCheckResult.builder()
                    .featureName("TEST_FEATURE")
                    .enabled(true)
                    .status("ENABLED")
                    .reason("Feature is enabled globally")
                    .build();
            when(restTemplate.getForEntity(anyString(), eq(FeatureCheckResult.class)))
                    .thenReturn(ResponseEntity.ok(serviceResult));

            client.check("TEST_FEATURE");

            // When
            client.evictCache("TEST_FEATURE");
            client.check("TEST_FEATURE");

            // Then - should call service twice (cache was evicted)
            verify(restTemplate, times(2)).getForEntity(anyString(), eq(FeatureCheckResult.class));
        }
    }

    @Nested
    @DisplayName("Direct Redis Mode Tests")
    class DirectRedisModeTests {

        private FeatureToggleClient client;

        @BeforeEach
        void setUp() {
            properties.getRedis().setDirectMode(true);
            client = new FeatureToggleClient(restTemplate, redisTemplate, properties);
        }

        @Test
        @DisplayName("isEnabled should read from Redis when ENABLED")
        void isEnabled_shouldReadFromRedis_whenEnabled() {
            // Given
            Map<Object, Object> toggleData = new HashMap<>();
            toggleData.put("status", "ENABLED");
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("feature:toggle:TEST_FEATURE")).thenReturn(toggleData);

            // When
            boolean result = client.isEnabled("TEST_FEATURE");

            // Then
            assertThat(result).isTrue();
            verify(restTemplate, never()).getForEntity(anyString(), any());
        }

        @Test
        @DisplayName("isEnabled should read from Redis when DISABLED")
        void isEnabled_shouldReadFromRedis_whenDisabled() {
            // Given
            Map<Object, Object> toggleData = new HashMap<>();
            toggleData.put("status", "DISABLED");
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("feature:toggle:TEST_FEATURE")).thenReturn(toggleData);

            // When
            boolean result = client.isEnabled("TEST_FEATURE");

            // Then
            assertThat(result).isFalse();
            verify(restTemplate, never()).getForEntity(anyString(), any());
        }

        @Test
        @DisplayName("isEnabled should check whitelist in LIST_MODE")
        void isEnabled_shouldCheckWhitelist_inListMode() {
            // Given
            Map<Object, Object> toggleData = new HashMap<>();
            toggleData.put("status", "LIST_MODE");
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(hashOperations.entries("feature:toggle:TEST_FEATURE")).thenReturn(toggleData);
            when(setOperations.isMember("feature:blacklist:TEST_FEATURE", "user1")).thenReturn(false);
            when(setOperations.isMember("feature:whitelist:TEST_FEATURE", "user1")).thenReturn(true);

            // When
            boolean result = client.isEnabled("TEST_FEATURE", "user1");

            // Then
            assertThat(result).isTrue();
            verify(restTemplate, never()).getForEntity(anyString(), any());
        }

        @Test
        @DisplayName("isEnabled should return false for blacklisted user")
        void isEnabled_shouldReturnFalse_forBlacklistedUser() {
            // Given
            Map<Object, Object> toggleData = new HashMap<>();
            toggleData.put("status", "LIST_MODE");
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(hashOperations.entries("feature:toggle:TEST_FEATURE")).thenReturn(toggleData);
            when(setOperations.isMember("feature:blacklist:TEST_FEATURE", "user1")).thenReturn(true);

            // When
            boolean result = client.isEnabled("TEST_FEATURE", "user1");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isEnabled should return false when user not in whitelist")
        void isEnabled_shouldReturnFalse_whenUserNotInWhitelist() {
            // Given
            Map<Object, Object> toggleData = new HashMap<>();
            toggleData.put("status", "LIST_MODE");
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(hashOperations.entries("feature:toggle:TEST_FEATURE")).thenReturn(toggleData);
            when(setOperations.isMember("feature:blacklist:TEST_FEATURE", "user1")).thenReturn(false);
            when(setOperations.isMember("feature:whitelist:TEST_FEATURE", "user1")).thenReturn(false);

            // When
            boolean result = client.isEnabled("TEST_FEATURE", "user1");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isEnabled should require userId for LIST_MODE")
        void isEnabled_shouldRequireUserId_forListMode() {
            // Given
            Map<Object, Object> toggleData = new HashMap<>();
            toggleData.put("status", "LIST_MODE");
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("feature:toggle:TEST_FEATURE")).thenReturn(toggleData);

            // When
            FeatureCheckResult result = client.check("TEST_FEATURE", null);

            // Then
            assertThat(result.isEnabled()).isFalse();
            assertThat(result.getReason()).isEqualTo("User ID required for list mode");
        }

        @Test
        @DisplayName("isEnabled should return default when feature not in Redis")
        void isEnabled_shouldReturnDefault_whenFeatureNotInRedis() {
            // Given
            properties.setGlobalDefault(FeatureToggleClientProperties.DefaultBehavior.DISABLED);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("feature:toggle:UNKNOWN")).thenReturn(new HashMap<>());

            // When
            boolean result = client.isEnabled("UNKNOWN");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isEnabled should fallback to HTTP when Redis fails")
        void isEnabled_shouldFallbackToHttp_whenRedisFails() {
            // Given
            when(redisTemplate.opsForHash()).thenThrow(new RuntimeException("Redis connection failed"));
            FeatureCheckResult serviceResult = FeatureCheckResult.builder()
                    .featureName("TEST_FEATURE")
                    .enabled(true)
                    .status("ENABLED")
                    .reason("Feature is enabled globally")
                    .build();
            when(restTemplate.getForEntity(anyString(), eq(FeatureCheckResult.class)))
                    .thenReturn(ResponseEntity.ok(serviceResult));

            // When
            boolean result = client.isEnabled("TEST_FEATURE");

            // Then
            assertThat(result).isTrue();
            verify(restTemplate).getForEntity(anyString(), eq(FeatureCheckResult.class));
        }

        @Test
        @DisplayName("check should cache Redis results locally")
        void check_shouldCacheRedisResults_locally() {
            // Given
            Map<Object, Object> toggleData = new HashMap<>();
            toggleData.put("status", "ENABLED");
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("feature:toggle:TEST_FEATURE")).thenReturn(toggleData);

            // When
            FeatureCheckResult firstCall = client.check("TEST_FEATURE");
            FeatureCheckResult secondCall = client.check("TEST_FEATURE");

            // Then
            assertThat(firstCall.isFromCache()).isFalse();
            assertThat(secondCall.isFromCache()).isTrue();
            verify(hashOperations, times(1)).entries(anyString());
        }
    }

    @Nested
    @DisplayName("Cache Invalidation Tests")
    class CacheInvalidationTests {

        private FeatureToggleClient client;

        @BeforeEach
        void setUp() {
            properties.getRedis().setDirectMode(false);
            client = new FeatureToggleClient(restTemplate, redisTemplate, properties);
        }

        @Test
        @DisplayName("onMessage should evict cache for specific feature")
        void onMessage_shouldEvictCache_forSpecificFeature() {
            // Given
            FeatureCheckResult serviceResult = FeatureCheckResult.builder()
                    .featureName("TEST_FEATURE")
                    .enabled(true)
                    .status("ENABLED")
                    .reason("Feature is enabled globally")
                    .build();
            when(restTemplate.getForEntity(anyString(), eq(FeatureCheckResult.class)))
                    .thenReturn(ResponseEntity.ok(serviceResult));

            client.check("TEST_FEATURE");

            // When - simulate Redis pub/sub message
            client.onMessage(new TestMessage("TEST_FEATURE".getBytes()), null);
            client.check("TEST_FEATURE");

            // Then - should call service twice
            verify(restTemplate, times(2)).getForEntity(anyString(), eq(FeatureCheckResult.class));
        }

        @Test
        @DisplayName("onMessage with * should evict all cache")
        void onMessage_withWildcard_shouldEvictAllCache() {
            // Given
            FeatureCheckResult result1 = FeatureCheckResult.builder()
                    .featureName("FEATURE_1")
                    .enabled(true)
                    .status("ENABLED")
                    .build();
            FeatureCheckResult result2 = FeatureCheckResult.builder()
                    .featureName("FEATURE_2")
                    .enabled(true)
                    .status("ENABLED")
                    .build();
            when(restTemplate.getForEntity(contains("FEATURE_1"), eq(FeatureCheckResult.class)))
                    .thenReturn(ResponseEntity.ok(result1));
            when(restTemplate.getForEntity(contains("FEATURE_2"), eq(FeatureCheckResult.class)))
                    .thenReturn(ResponseEntity.ok(result2));

            client.check("FEATURE_1");
            client.check("FEATURE_2");

            // When - simulate wildcard invalidation
            client.onMessage(new TestMessage("*".getBytes()), null);
            client.check("FEATURE_1");
            client.check("FEATURE_2");

            // Then - should call service 4 times (2 initial + 2 after eviction)
            verify(restTemplate, times(4)).getForEntity(anyString(), eq(FeatureCheckResult.class));
        }
    }

    // Helper class for testing message handling
    private static class TestMessage implements org.springframework.data.redis.connection.Message {
        private final byte[] body;

        TestMessage(byte[] body) {
            this.body = body;
        }

        @Override
        public byte[] getBody() {
            return body;
        }

        @Override
        public byte[] getChannel() {
            return "feature-toggle-updates".getBytes();
        }
    }
}
