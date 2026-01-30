package io.raspiska.featuretoggle.dto;

import io.raspiska.featuretoggle.entity.ToggleStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFeatureToggleRequest {

    @NotNull(message = "Status is required")
    private ToggleStatus status;

    private String description;

    private String groupName;
}
