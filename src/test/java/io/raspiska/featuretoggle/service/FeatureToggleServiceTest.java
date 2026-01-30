package io.raspiska.featuretoggle.service;

import io.raspiska.featuretoggle.dto.*;
import io.raspiska.featuretoggle.entity.FeatureToggle;
import io.raspiska.featuretoggle.entity.FeatureToggleUser;
import io.raspiska.featuretoggle.entity.FeatureToggleUser.ListType;
import io.raspiska.featuretoggle.entity.ToggleStatus;
import io.raspiska.featuretoggle.repository.FeatureToggleRepository;
import io.raspiska.featuretoggle.repository.FeatureToggleUserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureToggleServiceTest {

    @Mock
    private FeatureToggleRepository toggleRepository;

    @Mock
    private FeatureToggleUserRepository userRepository;

    @Mock
    private FeatureToggleCacheService cacheService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private FeatureToggleService service;

    private FeatureToggle testToggle;

    @BeforeEach
    void setUp() {
        testToggle = FeatureToggle.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.ENABLED)
                .description("Test description")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("getAllToggles should return all toggles as DTOs")
    void getAllToggles_shouldReturnAllToggles() {
        // Given
        FeatureToggle toggle2 = FeatureToggle.builder()
                .id(2L)
                .featureName("FEATURE_2")
                .status(ToggleStatus.DISABLED)
                .build();
        when(toggleRepository.findAll()).thenReturn(List.of(testToggle, toggle2));
        when(userRepository.countByFeatureAndListType(any(), eq(ListType.WHITELIST))).thenReturn(0L);
        when(userRepository.countByFeatureAndListType(any(), eq(ListType.BLACKLIST))).thenReturn(0L);

        // When
        List<FeatureToggleDto> result = service.getAllToggles();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFeatureName()).isEqualTo("TEST_FEATURE");
        assertThat(result.get(1).getFeatureName()).isEqualTo("FEATURE_2");
    }

    @Test
    @DisplayName("getToggle should return toggle DTO when found")
    void getToggle_shouldReturnToggle_whenFound() {
        // Given
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(testToggle));
        when(userRepository.countByFeatureAndListType(testToggle, ListType.WHITELIST)).thenReturn(5L);
        when(userRepository.countByFeatureAndListType(testToggle, ListType.BLACKLIST)).thenReturn(2L);

        // When
        FeatureToggleDto result = service.getToggle("TEST_FEATURE");

        // Then
        assertThat(result.getFeatureName()).isEqualTo("TEST_FEATURE");
        assertThat(result.getStatus()).isEqualTo(ToggleStatus.ENABLED);
        assertThat(result.getWhitelistCount()).isEqualTo(5L);
        assertThat(result.getBlacklistCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getToggle should throw EntityNotFoundException when not found")
    void getToggle_shouldThrowException_whenNotFound() {
        // Given
        when(toggleRepository.findByFeatureName("UNKNOWN")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> service.getToggle("UNKNOWN"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("createToggle should create and return new toggle")
    void createToggle_shouldCreateToggle() {
        // Given
        CreateFeatureToggleRequest request = new CreateFeatureToggleRequest();
        request.setFeatureName("NEW_FEATURE");
        request.setStatus(ToggleStatus.ENABLED);
        request.setDescription("New feature");

        when(toggleRepository.existsByFeatureName("NEW_FEATURE")).thenReturn(false);
        when(toggleRepository.save(any(FeatureToggle.class))).thenAnswer(inv -> {
            FeatureToggle t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(userRepository.countByFeatureAndListType(any(), any())).thenReturn(0L);

        // When
        FeatureToggleDto result = service.createToggle(request, "test-actor");

        // Then
        assertThat(result.getFeatureName()).isEqualTo("NEW_FEATURE");
        assertThat(result.getStatus()).isEqualTo(ToggleStatus.ENABLED);
        verify(cacheService).invalidateCache("NEW_FEATURE");
    }

    @Test
    @DisplayName("createToggle should throw exception when toggle already exists")
    void createToggle_shouldThrowException_whenAlreadyExists() {
        // Given
        CreateFeatureToggleRequest request = new CreateFeatureToggleRequest();
        request.setFeatureName("EXISTING");
        request.setStatus(ToggleStatus.ENABLED);

        when(toggleRepository.existsByFeatureName("EXISTING")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> service.createToggle(request, "test-actor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("updateToggle should update status and invalidate cache")
    void updateToggle_shouldUpdateAndInvalidateCache() {
        // Given
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(testToggle));
        when(toggleRepository.save(any(FeatureToggle.class))).thenReturn(testToggle);
        when(userRepository.countByFeatureAndListType(any(), any())).thenReturn(0L);

        UpdateFeatureToggleRequest request = new UpdateFeatureToggleRequest();
        request.setStatus(ToggleStatus.DISABLED);
        request.setDescription("Updated description");

        // When
        FeatureToggleDto result = service.updateToggle("TEST_FEATURE", request, "test-actor");

        // Then
        assertThat(testToggle.getStatus()).isEqualTo(ToggleStatus.DISABLED);
        assertThat(testToggle.getDescription()).isEqualTo("Updated description");
        verify(cacheService).invalidateCache("TEST_FEATURE");
    }

    @Test
    @DisplayName("updateToggle should not update description when null")
    void updateToggle_shouldNotUpdateDescription_whenNull() {
        // Given
        testToggle.setDescription("Original description");
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(testToggle));
        when(toggleRepository.save(any(FeatureToggle.class))).thenReturn(testToggle);
        when(userRepository.countByFeatureAndListType(any(), any())).thenReturn(0L);

        UpdateFeatureToggleRequest request = new UpdateFeatureToggleRequest();
        request.setStatus(ToggleStatus.DISABLED);
        request.setDescription(null);

        // When
        service.updateToggle("TEST_FEATURE", request, "test-actor");

        // Then
        assertThat(testToggle.getDescription()).isEqualTo("Original description");
    }

    @Test
    @DisplayName("deleteToggle should delete toggle and users")
    void deleteToggle_shouldDeleteToggleAndUsers() {
        // Given
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(testToggle));

        // When
        service.deleteToggle("TEST_FEATURE", "test-actor");

        // Then
        verify(userRepository).deleteByFeature(testToggle);
        verify(toggleRepository).delete(testToggle);
        verify(cacheService).invalidateCache("TEST_FEATURE");
    }

    @Test
    @DisplayName("addUsersToWhitelist should add new users only")
    void addUsersToWhitelist_shouldAddNewUsersOnly() {
        // Given
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(testToggle));
        when(userRepository.existsByFeatureAndUserIdAndListType(testToggle, "user1", ListType.WHITELIST)).thenReturn(false);
        when(userRepository.existsByFeatureAndUserIdAndListType(testToggle, "user2", ListType.WHITELIST)).thenReturn(true);

        // When
        service.addUsersToWhitelist("TEST_FEATURE", List.of("user1", "user2"), "test-actor");

        // Then
        ArgumentCaptor<List<FeatureToggleUser>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getUserId()).isEqualTo("user1");
        verify(cacheService).invalidateUserList("TEST_FEATURE", ListType.WHITELIST);
    }

    @Test
    @DisplayName("addUsersToBlacklist should add users to blacklist")
    void addUsersToBlacklist_shouldAddUsersToBlacklist() {
        // Given
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(testToggle));
        when(userRepository.existsByFeatureAndUserIdAndListType(any(), anyString(), eq(ListType.BLACKLIST))).thenReturn(false);

        // When
        service.addUsersToBlacklist("TEST_FEATURE", List.of("user1"), "test-actor");

        // Then
        ArgumentCaptor<List<FeatureToggleUser>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getListType()).isEqualTo(ListType.BLACKLIST);
        verify(cacheService).invalidateUserList("TEST_FEATURE", ListType.BLACKLIST);
    }

    @Test
    @DisplayName("removeUsersFromWhitelist should remove users and invalidate cache")
    void removeUsersFromWhitelist_shouldRemoveUsers() {
        // Given
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(testToggle));
        when(userRepository.deleteByFeatureAndUserIdInAndListType(testToggle, List.of("user1"), ListType.WHITELIST)).thenReturn(1);

        // When
        service.removeUsersFromWhitelist("TEST_FEATURE", List.of("user1"), "test-actor");

        // Then
        verify(userRepository).deleteByFeatureAndUserIdInAndListType(testToggle, List.of("user1"), ListType.WHITELIST);
        verify(cacheService).invalidateUserList("TEST_FEATURE", ListType.WHITELIST);
    }

    @Test
    @DisplayName("removeUsersFromBlacklist should remove users from blacklist")
    void removeUsersFromBlacklist_shouldRemoveUsers() {
        // Given
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(testToggle));
        when(userRepository.deleteByFeatureAndUserIdInAndListType(testToggle, List.of("user1"), ListType.BLACKLIST)).thenReturn(1);

        // When
        service.removeUsersFromBlacklist("TEST_FEATURE", List.of("user1"), "test-actor");

        // Then
        verify(userRepository).deleteByFeatureAndUserIdInAndListType(testToggle, List.of("user1"), ListType.BLACKLIST);
        verify(cacheService).invalidateUserList("TEST_FEATURE", ListType.BLACKLIST);
    }

    @Test
    @DisplayName("getWhitelistedUsers should return paginated users")
    void getWhitelistedUsers_shouldReturnPaginatedUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        FeatureToggleUser user = FeatureToggleUser.builder()
                .id(1L)
                .feature(testToggle)
                .userId("user1")
                .listType(ListType.WHITELIST)
                .build();
        Page<FeatureToggleUser> page = new PageImpl<>(List.of(user), pageable, 1);

        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(testToggle));
        when(userRepository.findByFeatureAndListType(testToggle, ListType.WHITELIST, pageable)).thenReturn(page);

        // When
        Page<String> result = service.getWhitelistedUsers("TEST_FEATURE", pageable);

        // Then
        assertThat(result.getContent()).containsExactly("user1");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getBlacklistedUsers should return paginated users")
    void getBlacklistedUsers_shouldReturnPaginatedUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        FeatureToggleUser user = FeatureToggleUser.builder()
                .id(1L)
                .feature(testToggle)
                .userId("blocked1")
                .listType(ListType.BLACKLIST)
                .build();
        Page<FeatureToggleUser> page = new PageImpl<>(List.of(user), pageable, 1);

        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(testToggle));
        when(userRepository.findByFeatureAndListType(testToggle, ListType.BLACKLIST, pageable)).thenReturn(page);

        // When
        Page<String> result = service.getBlacklistedUsers("TEST_FEATURE", pageable);

        // Then
        assertThat(result.getContent()).containsExactly("blocked1");
    }

    @Test
    @DisplayName("checkFeature should delegate to cache service")
    void checkFeature_shouldDelegateToCacheService() {
        // Given
        FeatureCheckResponse expectedResponse = FeatureCheckResponse.builder()
                .featureName("TEST_FEATURE")
                .enabled(true)
                .status(ToggleStatus.ENABLED)
                .reason("Feature is enabled globally")
                .build();
        when(cacheService.checkFeature("TEST_FEATURE", "user1")).thenReturn(expectedResponse);

        // When
        FeatureCheckResponse result = service.checkFeature("TEST_FEATURE", "user1");

        // Then
        assertThat(result).isEqualTo(expectedResponse);
        verify(cacheService).checkFeature("TEST_FEATURE", "user1");
    }

    @Test
    @DisplayName("addUsersToWhitelist should not save when all users already exist")
    void addUsersToWhitelist_shouldNotSave_whenAllUsersExist() {
        // Given
        when(toggleRepository.findByFeatureName("TEST_FEATURE")).thenReturn(Optional.of(testToggle));
        when(userRepository.existsByFeatureAndUserIdAndListType(testToggle, "user1", ListType.WHITELIST)).thenReturn(true);
        when(userRepository.existsByFeatureAndUserIdAndListType(testToggle, "user2", ListType.WHITELIST)).thenReturn(true);

        // When
        service.addUsersToWhitelist("TEST_FEATURE", List.of("user1", "user2"), "test-actor");

        // Then
        verify(userRepository, never()).saveAll(anyList());
        verify(cacheService).invalidateUserList("TEST_FEATURE", ListType.WHITELIST);
    }
}
