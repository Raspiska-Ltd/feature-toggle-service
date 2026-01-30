package io.raspiska.featuretoggle.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_feature_name", columnList = "feature_name"),
        @Index(name = "idx_audit_actor", columnList = "actor"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feature_name", nullable = false)
    private String featureName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column
    private String actor;

    @Column(length = 2000)
    private String details;

    @Column(nullable = false)
    private Instant timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = Instant.now();
    }

    public enum AuditAction {
        CREATE,
        UPDATE,
        DELETE,
        ADD_TO_WHITELIST,
        REMOVE_FROM_WHITELIST,
        ADD_TO_BLACKLIST,
        REMOVE_FROM_BLACKLIST,
        SCHEDULE,
        SCHEDULE_APPLIED
    }
}
