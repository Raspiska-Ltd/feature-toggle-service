package io.raspiska.featuretoggle;

import io.raspiska.featuretoggle.dto.CreateFeatureToggleRequest;
import io.raspiska.featuretoggle.dto.FeatureCheckResponse;
import io.raspiska.featuretoggle.dto.UpdateFeatureToggleRequest;
import io.raspiska.featuretoggle.dto.UserListRequest;
import io.raspiska.featuretoggle.entity.ToggleStatus;
import io.raspiska.featuretoggle.repository.FeatureToggleRepository;
import io.raspiska.featuretoggle.repository.FeatureToggleUserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FeatureToggleRedisIntegrationTest {

    private static final String REDIS_TOGGLE_PREFIX = "feature:toggle:";
    private static final String REDIS_WHITELIST_PREFIX = "feature:whitelist:";
    private static final String REDIS_BLACKLIST_PREFIX = "feature:blacklist:";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private FeatureToggleRepository toggleRepository;

    @Autowired
    private FeatureToggleUserRepository userRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1/toggles";
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        toggleRepository.deleteAll();
        // Clean Redis
        Set<String> keys = redisTemplate.keys("feature:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Create toggle should publish to Redis immediately")
    void createToggle_shouldPublishToRedis() throws InterruptedException {
        // Given
        String featureName = "TEST_FEATURE_CREATE";
        CreateFeatureToggleRequest request = new CreateFeatureToggleRequest();
        request.setFeatureName(featureName);
        request.setStatus(ToggleStatus.ENABLED);
        request.setDescription("Test feature");

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl, request, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Wait a bit for async cache population
        Thread.sleep(100);

        // Check feature via API (which triggers cache)
        ResponseEntity<FeatureCheckResponse> checkResponse = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check", FeatureCheckResponse.class);
        
        assertThat(checkResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(checkResponse.getBody()).isNotNull();
        assertThat(checkResponse.getBody().isEnabled()).isTrue();

        // Verify Redis has the toggle cached after check
        Thread.sleep(100);
        Map<Object, Object> redisData = redisTemplate.opsForHash().entries(REDIS_TOGGLE_PREFIX + featureName);
        assertThat(redisData).isNotEmpty();
        assertThat(redisData.get("status")).isEqualTo("ENABLED");
    }

    @Test
    @Order(2)
    @DisplayName("Update toggle should invalidate Redis cache")
    void updateToggle_shouldInvalidateRedisCache() throws InterruptedException {
        // Given - Create a toggle first
        String featureName = "TEST_FEATURE_UPDATE";
        CreateFeatureToggleRequest createRequest = new CreateFeatureToggleRequest();
        createRequest.setFeatureName(featureName);
        createRequest.setStatus(ToggleStatus.ENABLED);
        restTemplate.postForEntity(baseUrl, createRequest, Map.class);

        // Trigger cache population
        restTemplate.getForEntity(baseUrl + "/" + featureName + "/check", FeatureCheckResponse.class);
        Thread.sleep(100);

        // Verify initial cache state
        Map<Object, Object> initialRedisData = redisTemplate.opsForHash().entries(REDIS_TOGGLE_PREFIX + featureName);
        assertThat(initialRedisData.get("status")).isEqualTo("ENABLED");

        // When - Update the toggle
        UpdateFeatureToggleRequest updateRequest = new UpdateFeatureToggleRequest();
        updateRequest.setStatus(ToggleStatus.DISABLED);
        restTemplate.put(baseUrl + "/" + featureName, updateRequest);

        // Then - Cache should be invalidated
        Thread.sleep(100);
        Map<Object, Object> afterUpdateRedisData = redisTemplate.opsForHash().entries(REDIS_TOGGLE_PREFIX + featureName);
        // Cache is invalidated (empty) or updated
        assertThat(afterUpdateRedisData).isEmpty();

        // Verify the toggle is now disabled via API
        ResponseEntity<FeatureCheckResponse> checkResponse = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check", FeatureCheckResponse.class);
        assertThat(checkResponse.getBody()).isNotNull();
        assertThat(checkResponse.getBody().isEnabled()).isFalse();
        assertThat(checkResponse.getBody().getStatus()).isEqualTo(ToggleStatus.DISABLED);
    }

    @Test
    @Order(3)
    @DisplayName("Delete toggle should remove from Redis")
    void deleteToggle_shouldRemoveFromRedis() throws InterruptedException {
        // Given
        String featureName = "TEST_FEATURE_DELETE";
        CreateFeatureToggleRequest request = new CreateFeatureToggleRequest();
        request.setFeatureName(featureName);
        request.setStatus(ToggleStatus.ENABLED);
        restTemplate.postForEntity(baseUrl, request, Map.class);

        // Trigger cache population
        restTemplate.getForEntity(baseUrl + "/" + featureName + "/check", FeatureCheckResponse.class);
        Thread.sleep(100);

        // Verify cache exists
        assertThat(redisTemplate.opsForHash().entries(REDIS_TOGGLE_PREFIX + featureName)).isNotEmpty();

        // When
        restTemplate.delete(baseUrl + "/" + featureName);

        // Then
        Thread.sleep(100);
        Map<Object, Object> redisData = redisTemplate.opsForHash().entries(REDIS_TOGGLE_PREFIX + featureName);
        assertThat(redisData).isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("Add users to whitelist should update Redis set")
    void addUsersToWhitelist_shouldUpdateRedis() throws InterruptedException {
        // Given
        String featureName = "TEST_FEATURE_WHITELIST";
        CreateFeatureToggleRequest createRequest = new CreateFeatureToggleRequest();
        createRequest.setFeatureName(featureName);
        createRequest.setStatus(ToggleStatus.LIST_MODE);
        restTemplate.postForEntity(baseUrl, createRequest, Map.class);

        UserListRequest userListRequest = new UserListRequest();
        userListRequest.setUserIds(List.of("user1", "user2", "user3"));

        // When
        restTemplate.postForEntity(baseUrl + "/" + featureName + "/whitelist", userListRequest, Void.class);

        // Then - Check via API that users are whitelisted
        Thread.sleep(100);
        
        ResponseEntity<FeatureCheckResponse> user1Check = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check?userId=user1", FeatureCheckResponse.class);
        assertThat(user1Check.getBody()).isNotNull();
        assertThat(user1Check.getBody().isEnabled()).isTrue();
        assertThat(user1Check.getBody().getReason()).isEqualTo("User is whitelisted");

        ResponseEntity<FeatureCheckResponse> nonWhitelistedCheck = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check?userId=user999", FeatureCheckResponse.class);
        assertThat(nonWhitelistedCheck.getBody()).isNotNull();
        assertThat(nonWhitelistedCheck.getBody().isEnabled()).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("Add users to blacklist should update Redis and block access")
    void addUsersToBlacklist_shouldBlockAccess() throws InterruptedException {
        // Given
        String featureName = "TEST_FEATURE_BLACKLIST";
        CreateFeatureToggleRequest createRequest = new CreateFeatureToggleRequest();
        createRequest.setFeatureName(featureName);
        createRequest.setStatus(ToggleStatus.LIST_MODE);
        restTemplate.postForEntity(baseUrl, createRequest, Map.class);

        // Add user to whitelist first
        UserListRequest whitelistRequest = new UserListRequest();
        whitelistRequest.setUserIds(List.of("user1", "blockedUser"));
        restTemplate.postForEntity(baseUrl + "/" + featureName + "/whitelist", whitelistRequest, Void.class);

        // When - Add user to blacklist (blacklist takes precedence)
        UserListRequest blacklistRequest = new UserListRequest();
        blacklistRequest.setUserIds(List.of("blockedUser"));
        restTemplate.postForEntity(baseUrl + "/" + featureName + "/blacklist", blacklistRequest, Void.class);

        // Then
        Thread.sleep(100);

        // Whitelisted user should have access
        ResponseEntity<FeatureCheckResponse> user1Check = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check?userId=user1", FeatureCheckResponse.class);
        assertThat(user1Check.getBody()).isNotNull();
        assertThat(user1Check.getBody().isEnabled()).isTrue();

        // Blacklisted user should be blocked (even if whitelisted)
        ResponseEntity<FeatureCheckResponse> blockedCheck = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check?userId=blockedUser", FeatureCheckResponse.class);
        assertThat(blockedCheck.getBody()).isNotNull();
        assertThat(blockedCheck.getBody().isEnabled()).isFalse();
        assertThat(blockedCheck.getBody().getReason()).isEqualTo("User is blacklisted");
    }

    @Test
    @Order(6)
    @DisplayName("Remove users from whitelist should update Redis")
    void removeUsersFromWhitelist_shouldUpdateRedis() throws InterruptedException {
        // Given
        String featureName = "TEST_FEATURE_REMOVE_WHITELIST";
        CreateFeatureToggleRequest createRequest = new CreateFeatureToggleRequest();
        createRequest.setFeatureName(featureName);
        createRequest.setStatus(ToggleStatus.LIST_MODE);
        restTemplate.postForEntity(baseUrl, createRequest, Map.class);

        UserListRequest addRequest = new UserListRequest();
        addRequest.setUserIds(List.of("user1", "user2"));
        restTemplate.postForEntity(baseUrl + "/" + featureName + "/whitelist", addRequest, Void.class);

        // Verify user1 has access
        Thread.sleep(100);
        ResponseEntity<FeatureCheckResponse> beforeRemove = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check?userId=user1", FeatureCheckResponse.class);
        assertThat(beforeRemove.getBody().isEnabled()).isTrue();

        // When - Remove user1 from whitelist (using POST /remove endpoint)
        UserListRequest removeRequest = new UserListRequest();
        removeRequest.setUserIds(List.of("user1"));
        ResponseEntity<Void> deleteResponse = restTemplate.postForEntity(
                baseUrl + "/" + featureName + "/whitelist/remove",
                removeRequest,
                Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Then - Wait for cache invalidation to propagate
        Thread.sleep(200);
        
        // Clear any Redis cached user list by making a fresh check
        ResponseEntity<FeatureCheckResponse> afterRemove = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check?userId=user1", FeatureCheckResponse.class);
        assertThat(afterRemove.getBody()).isNotNull();
        assertThat(afterRemove.getBody().isEnabled()).as("user1 should be disabled after removal from whitelist").isFalse();

        // user2 should still have access
        ResponseEntity<FeatureCheckResponse> user2Check = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check?userId=user2", FeatureCheckResponse.class);
        assertThat(user2Check.getBody().isEnabled()).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("Toggle status change from ENABLED to LIST_MODE should work correctly")
    void toggleStatusChange_shouldWorkCorrectly() throws InterruptedException {
        // Given - Create enabled toggle
        String featureName = "TEST_FEATURE_STATUS_CHANGE";
        CreateFeatureToggleRequest createRequest = new CreateFeatureToggleRequest();
        createRequest.setFeatureName(featureName);
        createRequest.setStatus(ToggleStatus.ENABLED);
        restTemplate.postForEntity(baseUrl, createRequest, Map.class);

        // Verify enabled for everyone
        ResponseEntity<FeatureCheckResponse> enabledCheck = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check?userId=anyUser", FeatureCheckResponse.class);
        assertThat(enabledCheck.getBody().isEnabled()).isTrue();

        // When - Change to LIST_MODE
        UpdateFeatureToggleRequest updateRequest = new UpdateFeatureToggleRequest();
        updateRequest.setStatus(ToggleStatus.LIST_MODE);
        restTemplate.put(baseUrl + "/" + featureName, updateRequest);

        // Then - User should not have access (not in whitelist)
        Thread.sleep(100);
        ResponseEntity<FeatureCheckResponse> listModeCheck = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check?userId=anyUser", FeatureCheckResponse.class);
        assertThat(listModeCheck.getBody()).isNotNull();
        assertThat(listModeCheck.getBody().isEnabled()).isFalse();
        assertThat(listModeCheck.getBody().getStatus()).isEqualTo(ToggleStatus.LIST_MODE);
    }

    @Test
    @Order(8)
    @DisplayName("Cache invalidation should propagate to local cache")
    void cacheInvalidation_shouldPropagateToLocalCache() throws InterruptedException {
        // Given
        String featureName = "TEST_FEATURE_CACHE_INVALIDATION";
        CreateFeatureToggleRequest createRequest = new CreateFeatureToggleRequest();
        createRequest.setFeatureName(featureName);
        createRequest.setStatus(ToggleStatus.ENABLED);
        restTemplate.postForEntity(baseUrl, createRequest, Map.class);

        // Populate cache by checking
        restTemplate.getForEntity(baseUrl + "/" + featureName + "/check", FeatureCheckResponse.class);
        Thread.sleep(100);

        // When - Update toggle
        UpdateFeatureToggleRequest updateRequest = new UpdateFeatureToggleRequest();
        updateRequest.setStatus(ToggleStatus.DISABLED);
        restTemplate.put(baseUrl + "/" + featureName, updateRequest);

        // Then - Immediate check should return new status (cache invalidated)
        ResponseEntity<FeatureCheckResponse> checkResponse = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check", FeatureCheckResponse.class);
        assertThat(checkResponse.getBody()).isNotNull();
        assertThat(checkResponse.getBody().isEnabled()).isFalse();
        assertThat(checkResponse.getBody().getStatus()).isEqualTo(ToggleStatus.DISABLED);
    }

    @Test
    @Order(9)
    @DisplayName("Bulk user operations should be efficient")
    void bulkUserOperations_shouldBeEfficient() throws InterruptedException {
        // Given
        String featureName = "TEST_FEATURE_BULK";
        CreateFeatureToggleRequest createRequest = new CreateFeatureToggleRequest();
        createRequest.setFeatureName(featureName);
        createRequest.setStatus(ToggleStatus.LIST_MODE);
        restTemplate.postForEntity(baseUrl, createRequest, Map.class);

        // When - Add 100 users
        List<String> userIds = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            userIds.add("bulkUser" + i);
        }
        UserListRequest bulkRequest = new UserListRequest();
        bulkRequest.setUserIds(userIds);

        long startTime = System.currentTimeMillis();
        restTemplate.postForEntity(baseUrl + "/" + featureName + "/whitelist", bulkRequest, Void.class);
        long duration = System.currentTimeMillis() - startTime;

        // Then - Should complete in reasonable time
        assertThat(duration).isLessThan(5000); // Less than 5 seconds

        // Verify random users have access
        Thread.sleep(100);
        ResponseEntity<FeatureCheckResponse> check50 = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check?userId=bulkUser50", FeatureCheckResponse.class);
        assertThat(check50.getBody().isEnabled()).isTrue();

        ResponseEntity<FeatureCheckResponse> check99 = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check?userId=bulkUser99", FeatureCheckResponse.class);
        assertThat(check99.getBody().isEnabled()).isTrue();
    }

    @Test
    @Order(10)
    @DisplayName("Feature check without userId for LIST_MODE should return disabled")
    void featureCheckWithoutUserId_forListMode_shouldReturnDisabled() {
        // Given
        String featureName = "TEST_FEATURE_NO_USER";
        CreateFeatureToggleRequest createRequest = new CreateFeatureToggleRequest();
        createRequest.setFeatureName(featureName);
        createRequest.setStatus(ToggleStatus.LIST_MODE);
        restTemplate.postForEntity(baseUrl, createRequest, Map.class);

        // When
        ResponseEntity<FeatureCheckResponse> checkResponse = restTemplate.getForEntity(
                baseUrl + "/" + featureName + "/check", FeatureCheckResponse.class);

        // Then
        assertThat(checkResponse.getBody()).isNotNull();
        assertThat(checkResponse.getBody().isEnabled()).isFalse();
        assertThat(checkResponse.getBody().getReason()).isEqualTo("User ID required for list mode");
    }

    @Test
    @Order(11)
    @DisplayName("Non-existent feature should return not found response")
    void nonExistentFeature_shouldReturnNotFound() {
        // When
        ResponseEntity<FeatureCheckResponse> checkResponse = restTemplate.getForEntity(
                baseUrl + "/NON_EXISTENT_FEATURE/check", FeatureCheckResponse.class);

        // Then
        assertThat(checkResponse.getBody()).isNotNull();
        assertThat(checkResponse.getBody().isEnabled()).isFalse();
        assertThat(checkResponse.getBody().getReason()).isEqualTo("Feature not found");
    }
}
