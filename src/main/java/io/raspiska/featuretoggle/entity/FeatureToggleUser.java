package io.raspiska.featuretoggle.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "feature_toggle_users", indexes = {
        @Index(name = "idx_feature_user", columnList = "feature_id, user_id"),
        @Index(name = "idx_feature_list_type", columnList = "feature_id, list_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureToggleUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id", nullable = false)
    private FeatureToggle feature;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "list_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ListType listType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public enum ListType {
        WHITELIST,
        BLACKLIST
    }
}
