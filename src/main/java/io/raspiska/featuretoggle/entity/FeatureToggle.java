package io.raspiska.featuretoggle.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "feature_toggles", indexes = {
        @Index(name = "idx_feature_name", columnList = "feature_name"),
        @Index(name = "idx_group_name", columnList = "group_name"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_scheduled_at", columnList = "scheduled_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureToggle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feature_name", nullable = false, unique = true)
    private String featureName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ToggleStatus status;

    @Column(length = 500)
    private String description;

    @Column(name = "group_name")
    @Builder.Default
    private String groupName = "default";

    @Column(name = "scheduled_status")
    @Enumerated(EnumType.STRING)
    private ToggleStatus scheduledStatus;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
