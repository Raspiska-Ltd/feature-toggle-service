package io.raspiska.featuretoggle.dto;

import io.raspiska.featuretoggle.entity.ToggleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureToggleDto {

    private Long id;
    private String featureName;
    private ToggleStatus status;
    private String description;
    private long whitelistCount;
    private long blacklistCount;
    private Instant createdAt;
    private Instant updatedAt;
}
