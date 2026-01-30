package io.raspiska.featuretoggle.service;

import io.raspiska.featuretoggle.dto.*;
import io.raspiska.featuretoggle.entity.AuditLog.AuditAction;
import io.raspiska.featuretoggle.entity.ToggleStatus;
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
    private final AuditLogService auditLogService;

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
    public FeatureToggleDto createToggle(CreateFeatureToggleRequest request, String actor) {
        if (toggleRepository.existsByFeatureName(request.getFeatureName())) {
            throw new IllegalArgumentException("Feature toggle already exists: " + request.getFeatureName());
        }

        FeatureToggle toggle = FeatureToggle.builder()
                .featureName(request.getFeatureName())
                .status(request.getStatus())
                .description(request.getDescription())
                .groupName(request.getGroupName() != null ? request.getGroupName() : "default")
                .build();

        toggle = toggleRepository.save(toggle);
        log.info("Created feature toggle: {}", toggle.getFeatureName());

        cacheService.invalidateCache(toggle.getFeatureName());
        auditLogService.log(toggle.getFeatureName(), AuditAction.CREATE, actor, 
                "Created with status: " + toggle.getStatus());
        return toDto(toggle);
    }

    @Transactional
    public FeatureToggleDto updateToggle(String featureName, UpdateFeatureToggleRequest request, String actor) {
        FeatureToggle toggle = findByName(featureName);
        String oldStatus = toggle.getStatus().name();

        toggle.setStatus(request.getStatus());
        if (request.getDescription() != null) {
            toggle.setDescription(request.getDescription());
        }
        if (request.getGroupName() != null) {
            toggle.setGroupName(request.getGroupName());
        }

        toggle = toggleRepository.save(toggle);
        log.info("Updated feature toggle: {} to status: {}", featureName, request.getStatus());

        cacheService.invalidateCache(featureName);
        auditLogService.log(featureName, AuditAction.UPDATE, actor, 
                "Status changed from " + oldStatus + " to " + request.getStatus());
        return toDto(toggle);
    }

    @Transactional
    public void deleteToggle(String featureName, String actor) {
        FeatureToggle toggle = findByName(featureName);
        
        userRepository.deleteByFeature(toggle);
        toggleRepository.delete(toggle);
        
        log.info("Deleted feature toggle: {}", featureName);
        cacheService.invalidateCache(featureName);
        auditLogService.log(featureName, AuditAction.DELETE, actor, "Toggle deleted");
    }

    @Transactional
    public void addUsersToWhitelist(String featureName, List<String> userIds, String actor) {
        addUsersToList(featureName, userIds, ListType.WHITELIST, actor);
    }

    @Transactional
    public void addUsersToBlacklist(String featureName, List<String> userIds, String actor) {
        addUsersToList(featureName, userIds, ListType.BLACKLIST, actor);
    }

    @Transactional
    public void removeUsersFromWhitelist(String featureName, List<String> userIds, String actor) {
        removeUsersFromList(featureName, userIds, ListType.WHITELIST, actor);
    }

    @Transactional
    public void removeUsersFromBlacklist(String featureName, List<String> userIds, String actor) {
        removeUsersFromList(featureName, userIds, ListType.BLACKLIST, actor);
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

    private void addUsersToList(String featureName, List<String> userIds, ListType listType, String actor) {
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
            AuditAction action = listType == ListType.WHITELIST ? AuditAction.ADD_TO_WHITELIST : AuditAction.ADD_TO_BLACKLIST;
            auditLogService.log(featureName, action, actor, "Added " + usersToAdd.size() + " users");
        }

        cacheService.invalidateUserList(featureName, listType);
    }

    private void removeUsersFromList(String featureName, List<String> userIds, ListType listType, String actor) {
        FeatureToggle toggle = findByName(featureName);
        
        int deleted = userRepository.deleteByFeatureAndUserIdInAndListType(toggle, userIds, listType);
        log.info("Removed {} users from {} for feature: {}", deleted, listType, featureName);

        if (deleted > 0) {
            AuditAction action = listType == ListType.WHITELIST ? AuditAction.REMOVE_FROM_WHITELIST : AuditAction.REMOVE_FROM_BLACKLIST;
            auditLogService.log(featureName, action, actor, "Removed " + deleted + " users");
        }

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

    @Transactional(readOnly = true)
    public List<FeatureToggleDto> getTogglesByGroup(String groupName) {
        return toggleRepository.findByGroupName(groupName).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public FeatureToggleDto scheduleToggle(String featureName, ToggleStatus scheduledStatus, 
                                            java.time.Instant scheduledAt, String actor) {
        FeatureToggle toggle = findByName(featureName);
        toggle.setScheduledStatus(scheduledStatus);
        toggle.setScheduledAt(scheduledAt);
        toggle = toggleRepository.save(toggle);
        
        log.info("Scheduled toggle {} to change to {} at {}", featureName, scheduledStatus, scheduledAt);
        auditLogService.log(featureName, AuditAction.SCHEDULE, actor, 
                "Scheduled to change to " + scheduledStatus + " at " + scheduledAt);
        
        return toDto(toggle);
    }

    @Transactional
    public FeatureToggleDto cancelSchedule(String featureName, String actor) {
        FeatureToggle toggle = findByName(featureName);
        toggle.setScheduledStatus(null);
        toggle.setScheduledAt(null);
        toggle = toggleRepository.save(toggle);
        
        log.info("Cancelled scheduled toggle change for {}", featureName);
        auditLogService.log(featureName, AuditAction.SCHEDULE, actor, "Cancelled scheduled change");
        
        return toDto(toggle);
    }

    private FeatureToggleDto toDto(FeatureToggle toggle) {
        return FeatureToggleDto.builder()
                .id(toggle.getId())
                .featureName(toggle.getFeatureName())
                .status(toggle.getStatus())
                .description(toggle.getDescription())
                .groupName(toggle.getGroupName())
                .scheduledStatus(toggle.getScheduledStatus())
                .scheduledAt(toggle.getScheduledAt())
                .whitelistCount(userRepository.countByFeatureAndListType(toggle, ListType.WHITELIST))
                .blacklistCount(userRepository.countByFeatureAndListType(toggle, ListType.BLACKLIST))
                .createdAt(toggle.getCreatedAt())
                .updatedAt(toggle.getUpdatedAt())
                .build();
    }
}
