package io.raspiska.featuretoggle.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListRequest {

    @NotEmpty(message = "User IDs list cannot be empty")
    private List<String> userIds;
}
