package io.raspiska.featuretoggle.dto;

import io.raspiska.featuretoggle.entity.ToggleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFeatureToggleRequest {

    @NotBlank(message = "Feature name is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Feature name must be uppercase with underscores (e.g., WITHDRAW, WITHDRAW_BANK_X)")
    private String featureName;

    @NotNull(message = "Status is required")
    private ToggleStatus status;

    private String description;

    private String groupName;
}
