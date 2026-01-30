package io.raspiska.featuretoggle.dto;

import io.raspiska.featuretoggle.entity.ToggleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureCheckResponse {

    private String featureName;
    private boolean enabled;
    private ToggleStatus status;
    private String reason;
}
