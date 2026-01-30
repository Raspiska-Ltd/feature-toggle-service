package io.raspiska.featuretoggle.dto;

import io.raspiska.featuretoggle.entity.ToggleStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleToggleRequest {

    @NotNull(message = "Scheduled status is required")
    private ToggleStatus scheduledStatus;

    @NotNull(message = "Scheduled time is required")
    @Future(message = "Scheduled time must be in the future")
    private Instant scheduledAt;
}
