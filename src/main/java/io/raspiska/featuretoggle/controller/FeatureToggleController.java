package io.raspiska.featuretoggle.controller;

import io.raspiska.featuretoggle.dto.*;
import io.raspiska.featuretoggle.service.FeatureToggleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/toggles")
@RequiredArgsConstructor
public class FeatureToggleController {

    private final FeatureToggleService toggleService;

    @GetMapping
    public ResponseEntity<List<FeatureToggleDto>> getAllToggles(
            @RequestParam(required = false) String group) {
        if (group != null) {
            return ResponseEntity.ok(toggleService.getTogglesByGroup(group));
        }
        return ResponseEntity.ok(toggleService.getAllToggles());
    }

    @GetMapping("/{featureName}")
    public ResponseEntity<FeatureToggleDto> getToggle(@PathVariable String featureName) {
        return ResponseEntity.ok(toggleService.getToggle(featureName));
    }

    @PostMapping
    public ResponseEntity<FeatureToggleDto> createToggle(
            @Valid @RequestBody CreateFeatureToggleRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toggleService.createToggle(request, actor));
    }

    @PutMapping("/{featureName}")
    public ResponseEntity<FeatureToggleDto> updateToggle(
            @PathVariable String featureName,
            @Valid @RequestBody UpdateFeatureToggleRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        return ResponseEntity.ok(toggleService.updateToggle(featureName, request, actor));
    }

    @DeleteMapping("/{featureName}")
    public ResponseEntity<Void> deleteToggle(
            @PathVariable String featureName,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        toggleService.deleteToggle(featureName, actor);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{featureName}/check")
    public ResponseEntity<FeatureCheckResponse> checkFeature(
            @PathVariable String featureName,
            @RequestParam(required = false) String userId) {
        return ResponseEntity.ok(toggleService.checkFeature(featureName, userId));
    }

    @PostMapping("/{featureName}/whitelist")
    public ResponseEntity<Void> addToWhitelist(
            @PathVariable String featureName,
            @Valid @RequestBody UserListRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        toggleService.addUsersToWhitelist(featureName, request.getUserIds(), actor);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{featureName}/whitelist")
    public ResponseEntity<Void> removeFromWhitelist(
            @PathVariable String featureName,
            @RequestBody UserListRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        toggleService.removeUsersFromWhitelist(featureName, request.getUserIds(), actor);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{featureName}/whitelist/remove")
    public ResponseEntity<Void> removeFromWhitelistPost(
            @PathVariable String featureName,
            @Valid @RequestBody UserListRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        toggleService.removeUsersFromWhitelist(featureName, request.getUserIds(), actor);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{featureName}/whitelist")
    public ResponseEntity<Page<String>> getWhitelist(
            @PathVariable String featureName,
            Pageable pageable) {
        return ResponseEntity.ok(toggleService.getWhitelistedUsers(featureName, pageable));
    }

    @PostMapping("/{featureName}/blacklist")
    public ResponseEntity<Void> addToBlacklist(
            @PathVariable String featureName,
            @Valid @RequestBody UserListRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        toggleService.addUsersToBlacklist(featureName, request.getUserIds(), actor);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{featureName}/blacklist")
    public ResponseEntity<Void> removeFromBlacklist(
            @PathVariable String featureName,
            @RequestBody UserListRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        toggleService.removeUsersFromBlacklist(featureName, request.getUserIds(), actor);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{featureName}/blacklist/remove")
    public ResponseEntity<Void> removeFromBlacklistPost(
            @PathVariable String featureName,
            @Valid @RequestBody UserListRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        toggleService.removeUsersFromBlacklist(featureName, request.getUserIds(), actor);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{featureName}/blacklist")
    public ResponseEntity<Page<String>> getBlacklist(
            @PathVariable String featureName,
            Pageable pageable) {
        return ResponseEntity.ok(toggleService.getBlacklistedUsers(featureName, pageable));
    }

    @PostMapping("/{featureName}/schedule")
    public ResponseEntity<FeatureToggleDto> scheduleToggle(
            @PathVariable String featureName,
            @Valid @RequestBody ScheduleToggleRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        return ResponseEntity.ok(toggleService.scheduleToggle(
                featureName, request.getScheduledStatus(), request.getScheduledAt(), actor));
    }

    @DeleteMapping("/{featureName}/schedule")
    public ResponseEntity<FeatureToggleDto> cancelSchedule(
            @PathVariable String featureName,
            @RequestHeader(value = "X-Actor", required = false) String actor) {
        return ResponseEntity.ok(toggleService.cancelSchedule(featureName, actor));
    }
}
