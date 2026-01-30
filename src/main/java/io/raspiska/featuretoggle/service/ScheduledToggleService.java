package io.raspiska.featuretoggle.service;

import io.raspiska.featuretoggle.entity.AuditLog.AuditAction;
import io.raspiska.featuretoggle.entity.FeatureToggle;
import io.raspiska.featuretoggle.repository.FeatureToggleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledToggleService {

    private final FeatureToggleRepository toggleRepository;
    private final FeatureToggleCacheService cacheService;
    private final AuditLogService auditLogService;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processScheduledToggles() {
        List<FeatureToggle> scheduledToggles = toggleRepository
                .findByScheduledAtNotNullAndScheduledAtBefore(Instant.now());

        for (FeatureToggle toggle : scheduledToggles) {
            try {
                String oldStatus = toggle.getStatus().name();
                toggle.setStatus(toggle.getScheduledStatus());
                toggle.setScheduledStatus(null);
                toggle.setScheduledAt(null);
                toggleRepository.save(toggle);

                cacheService.invalidateCache(toggle.getFeatureName());
                
                auditLogService.log(
                        toggle.getFeatureName(),
                        AuditAction.SCHEDULE_APPLIED,
                        "SYSTEM",
                        "Scheduled status change from " + oldStatus + " to " + toggle.getStatus()
                );

                log.info("Applied scheduled toggle change: {} -> {}", 
                        toggle.getFeatureName(), toggle.getStatus());
            } catch (Exception e) {
                log.error("Failed to apply scheduled toggle: {}", toggle.getFeatureName(), e);
            }
        }
    }
}
