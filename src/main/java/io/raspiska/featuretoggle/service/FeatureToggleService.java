package io.raspiska.featuretoggle.service;

import io.raspiska.featuretoggle.dto.*;
import io.raspiska.featuretoggle.entity.FeatureToggle;
import io.raspiska.featuretoggle.entity.FeatureToggleUser;
import io.raspiska.featuretoggle.entity.FeatureToggleUser.ListType;
import io.raspiska.featuretoggle.repository.FeatureToggleRepository;
import io.raspiska.featuretoggle.repository.FeatureToggleUserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureToggleService {

    private final FeatureToggleRepository toggleRepository;
    private final FeatureToggleUserRepository userRepository;
    private final FeatureToggleCacheService cacheService;

    @Transactional(readOnly = true)
    public List<FeatureToggleDto> getAllToggles() {
        return toggleRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeatureToggleDto getToggle(String featureName) {
        FeatureToggle toggle = findByName(featureName);
        return toDto(toggle);
    }

    @Transactional
    public FeatureToggleDto createToggle(CreateFeatureToggleRequest request) {
        if (toggleRepository.existsByFeatureName(request.getFeatureName())) {
            throw new IllegalArgumentException("Feature toggle already exists: " + request.getFeatureName());
        }

        FeatureToggle toggle = FeatureToggle.builder()
                .featureName(request.getFeatureName())
                .status(request.getStatus())
                .description(request.getDescription())
                .build();

        toggle = toggleRepository.save(toggle);
        log.info("Created feature toggle: {}", toggle.getFeatureName());

        cacheService.invalidateCache(toggle.getFeatureName());
        return toDto(toggle);
    }

    @Transactional
    public FeatureToggleDto updateToggle(String featureName, UpdateFeatureToggleRequest request) {
        FeatureToggle toggle = findByName(featureName);

        toggle.setStatus(request.getStatus());
        if (request.getDescription() != null) {
            toggle.setDescription(request.getDescription());
        }

        toggle = toggleRepository.save(toggle);
        log.info("Updated feature toggle: {} to status: {}", featureName, request.getStatus());

        cacheService.invalidateCache(featureName);
        return toDto(toggle);
    }

    @Transactional
    public void deleteToggle(String featureName) {
        FeatureToggle toggle = findByName(featureName);
        
        userRepository.deleteByFeature(toggle);
        toggleRepository.delete(toggle);
        
        log.info("Deleted feature toggle: {}", featureName);
        cacheService.invalidateCache(featureName);
    }

    @Transactional
    public void addUsersToWhitelist(String featureName, List<String> userIds) {
        addUsersToList(featureName, userIds, ListType.WHITELIST);
    }

    @Transactional
    public void addUsersToBlacklist(String featureName, List<String> userIds) {
        addUsersToList(featureName, userIds, ListType.BLACKLIST);
    }

    @Transactional
    public void removeUsersFromWhitelist(String featureName, List<String> userIds) {
        removeUsersFromList(featureName, userIds, ListType.WHITELIST);
    }

    @Transactional
    public void removeUsersFromBlacklist(String featureName, List<String> userIds) {
        removeUsersFromList(featureName, userIds, ListType.BLACKLIST);
    }

    @Transactional(readOnly = true)
    public Page<String> getWhitelistedUsers(String featureName, Pageable pageable) {
        return getUsersFromList(featureName, ListType.WHITELIST, pageable);
    }

    @Transactional(readOnly = true)
    public Page<String> getBlacklistedUsers(String featureName, Pageable pageable) {
        return getUsersFromList(featureName, ListType.BLACKLIST, pageable);
    }

    public FeatureCheckResponse checkFeature(String featureName, String userId) {
        return cacheService.checkFeature(featureName, userId);
    }

    private void addUsersToList(String featureName, List<String> userIds, ListType listType) {
        FeatureToggle toggle = findByName(featureName);

        List<FeatureToggleUser> usersToAdd = new ArrayList<>();
        for (String userId : userIds) {
            if (!userRepository.existsByFeatureAndUserIdAndListType(toggle, userId, listType)) {
                usersToAdd.add(FeatureToggleUser.builder()
                        .feature(toggle)
                        .userId(userId)
                        .listType(listType)
                        .build());
            }
        }

        if (!usersToAdd.isEmpty()) {
            userRepository.saveAll(usersToAdd);
            log.info("Added {} users to {} for feature: {}", usersToAdd.size(), listType, featureName);
        }

        cacheService.invalidateUserList(featureName, listType);
    }

    private void removeUsersFromList(String featureName, List<String> userIds, ListType listType) {
        FeatureToggle toggle = findByName(featureName);
        
        int deleted = userRepository.deleteByFeatureAndUserIdInAndListType(toggle, userIds, listType);
        log.info("Removed {} users from {} for feature: {}", deleted, listType, featureName);

        cacheService.invalidateUserList(featureName, listType);
    }

    private Page<String> getUsersFromList(String featureName, ListType listType, Pageable pageable) {
        FeatureToggle toggle = findByName(featureName);
        return userRepository.findByFeatureAndListType(toggle, listType, pageable)
                .map(FeatureToggleUser::getUserId);
    }

    private FeatureToggle findByName(String featureName) {
        return toggleRepository.findByFeatureName(featureName)
                .orElseThrow(() -> new EntityNotFoundException("Feature toggle not found: " + featureName));
    }

    private FeatureToggleDto toDto(FeatureToggle toggle) {
        return FeatureToggleDto.builder()
                .id(toggle.getId())
                .featureName(toggle.getFeatureName())
                .status(toggle.getStatus())
                .description(toggle.getDescription())
                .whitelistCount(userRepository.countByFeatureAndListType(toggle, ListType.WHITELIST))
                .blacklistCount(userRepository.countByFeatureAndListType(toggle, ListType.BLACKLIST))
                .createdAt(toggle.getCreatedAt())
                .updatedAt(toggle.getUpdatedAt())
                .build();
    }
}
