package io.raspiska.featuretoggle.service;

import io.raspiska.featuretoggle.dto.AuditLogDto;
import io.raspiska.featuretoggle.entity.AuditLog;
import io.raspiska.featuretoggle.entity.AuditLog.AuditAction;
import io.raspiska.featuretoggle.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional
    public void log(String featureName, AuditAction action, String actor, String details) {
        AuditLog auditLog = AuditLog.builder()
                .featureName(featureName)
                .action(action)
                .actor(actor)
                .details(details)
                .build();
        auditLogRepository.save(auditLog);
        log.debug("Audit log: {} {} by {} - {}", action, featureName, actor, details);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogs(String featureName, String actor, Pageable pageable) {
        Page<AuditLog> logs;
        
        if (featureName != null && actor != null) {
            logs = auditLogRepository.findByFeatureNameAndActor(featureName, actor, pageable);
        } else if (featureName != null) {
            logs = auditLogRepository.findByFeatureName(featureName, pageable);
        } else if (actor != null) {
            logs = auditLogRepository.findByActor(actor, pageable);
        } else {
            logs = auditLogRepository.findAll(pageable);
        }
        
        return logs.map(this::toDto);
    }

    private AuditLogDto toDto(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .featureName(log.getFeatureName())
                .action(log.getAction())
                .actor(log.getActor())
                .details(log.getDetails())
                .timestamp(log.getTimestamp())
                .build();
    }
}
