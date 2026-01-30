package io.raspiska.featuretoggle.dto;

import io.raspiska.featuretoggle.entity.AuditLog.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {

    private Long id;
    private String featureName;
    private AuditAction action;
    private String actor;
    private String details;
    private Instant timestamp;
}
