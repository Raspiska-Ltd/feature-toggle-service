package io.raspiska.featuretoggle.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureCheckResult {

    private String featureName;
    private boolean enabled;
    private String status;
    private String reason;
    private boolean fromCache;
    private boolean fromDefault;
}
