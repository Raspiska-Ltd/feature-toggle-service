package io.raspiska.featuretoggle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.raspiska.featuretoggle.dto.*;
import io.raspiska.featuretoggle.entity.ToggleStatus;
import io.raspiska.featuretoggle.service.FeatureToggleService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeatureToggleController.class)
class FeatureToggleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FeatureToggleService toggleService;

    @Test
    @DisplayName("GET /api/v1/toggles should return all toggles")
    void getAllToggles_shouldReturnAllToggles() throws Exception {
        // Given
        FeatureToggleDto toggle1 = FeatureToggleDto.builder()
                .id(1L)
                .featureName("FEATURE_1")
                .status(ToggleStatus.ENABLED)
                .build();
        FeatureToggleDto toggle2 = FeatureToggleDto.builder()
                .id(2L)
                .featureName("FEATURE_2")
                .status(ToggleStatus.DISABLED)
                .build();
        when(toggleService.getAllToggles()).thenReturn(List.of(toggle1, toggle2));

        // When/Then
        mockMvc.perform(get("/api/v1/toggles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].featureName").value("FEATURE_1"))
                .andExpect(jsonPath("$[1].featureName").value("FEATURE_2"));
    }

    @Test
    @DisplayName("GET /api/v1/toggles/{name} should return toggle")
    void getToggle_shouldReturnToggle() throws Exception {
        // Given
        FeatureToggleDto toggle = FeatureToggleDto.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.ENABLED)
                .description("Test")
                .whitelistCount(5L)
                .blacklistCount(2L)
                .createdAt(Instant.now())
                .build();
        when(toggleService.getToggle("TEST_FEATURE")).thenReturn(toggle);

        // When/Then
        mockMvc.perform(get("/api/v1/toggles/TEST_FEATURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureName").value("TEST_FEATURE"))
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.whitelistCount").value(5));
    }

    @Test
    @DisplayName("POST /api/v1/toggles should create toggle")
    void createToggle_shouldCreateToggle() throws Exception {
        // Given
        CreateFeatureToggleRequest request = new CreateFeatureToggleRequest();
        request.setFeatureName("NEW_FEATURE");
        request.setStatus(ToggleStatus.ENABLED);
        request.setDescription("New feature");

        FeatureToggleDto created = FeatureToggleDto.builder()
                .id(1L)
                .featureName("NEW_FEATURE")
                .status(ToggleStatus.ENABLED)
                .description("New feature")
                .build();
        when(toggleService.createToggle(any(), any())).thenReturn(created);

        // When/Then
        mockMvc.perform(post("/api/v1/toggles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.featureName").value("NEW_FEATURE"));
    }

    @Test
    @DisplayName("PUT /api/v1/toggles/{name} should update toggle")
    void updateToggle_shouldUpdateToggle() throws Exception {
        // Given
        UpdateFeatureToggleRequest request = new UpdateFeatureToggleRequest();
        request.setStatus(ToggleStatus.DISABLED);

        FeatureToggleDto updated = FeatureToggleDto.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.DISABLED)
                .build();
        when(toggleService.updateToggle(eq("TEST_FEATURE"), any(), any())).thenReturn(updated);

        // When/Then
        mockMvc.perform(put("/api/v1/toggles/TEST_FEATURE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/toggles/{name} should delete toggle")
    void deleteToggle_shouldDeleteToggle() throws Exception {
        // Given
        doNothing().when(toggleService).deleteToggle(eq("TEST_FEATURE"), any());

        // When/Then
        mockMvc.perform(delete("/api/v1/toggles/TEST_FEATURE"))
                .andExpect(status().isNoContent());

        verify(toggleService).deleteToggle(eq("TEST_FEATURE"), any());
    }

    @Test
    @DisplayName("GET /api/v1/toggles/{name}/check should check feature")
    void checkFeature_shouldCheckFeature() throws Exception {
        // Given
        FeatureCheckResponse response = FeatureCheckResponse.builder()
                .featureName("TEST_FEATURE")
                .enabled(true)
                .status(ToggleStatus.ENABLED)
                .reason("Feature is enabled globally")
                .build();
        when(toggleService.checkFeature("TEST_FEATURE", "user1")).thenReturn(response);

        // When/Then
        mockMvc.perform(get("/api/v1/toggles/TEST_FEATURE/check")
                        .param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("Feature is enabled globally"));
    }

    @Test
    @DisplayName("GET /api/v1/toggles/{name}/check without userId should work")
    void checkFeature_withoutUserId_shouldWork() throws Exception {
        // Given
        FeatureCheckResponse response = FeatureCheckResponse.builder()
                .featureName("TEST_FEATURE")
                .enabled(true)
                .status(ToggleStatus.ENABLED)
                .reason("Feature is enabled globally")
                .build();
        when(toggleService.checkFeature("TEST_FEATURE", null)).thenReturn(response);

        // When/Then
        mockMvc.perform(get("/api/v1/toggles/TEST_FEATURE/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/toggles/{name}/whitelist should add users")
    void addToWhitelist_shouldAddUsers() throws Exception {
        // Given
        UserListRequest request = new UserListRequest();
        request.setUserIds(List.of("user1", "user2"));

        doNothing().when(toggleService).addUsersToWhitelist(eq("TEST_FEATURE"), anyList(), any());

        // When/Then
        mockMvc.perform(post("/api/v1/toggles/TEST_FEATURE/whitelist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(toggleService).addUsersToWhitelist(eq("TEST_FEATURE"), eq(List.of("user1", "user2")), any());
    }

    @Test
    @DisplayName("POST /api/v1/toggles/{name}/whitelist/remove should remove users")
    void removeFromWhitelist_shouldRemoveUsers() throws Exception {
        // Given
        UserListRequest request = new UserListRequest();
        request.setUserIds(List.of("user1"));

        doNothing().when(toggleService).removeUsersFromWhitelist(eq("TEST_FEATURE"), anyList(), any());

        // When/Then
        mockMvc.perform(post("/api/v1/toggles/TEST_FEATURE/whitelist/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(toggleService).removeUsersFromWhitelist(eq("TEST_FEATURE"), eq(List.of("user1")), any());
    }

    @Test
    @DisplayName("GET /api/v1/toggles/{name}/whitelist should return paginated users")
    void getWhitelist_shouldReturnPaginatedUsers() throws Exception {
        // Given
        when(toggleService.getWhitelistedUsers(eq("TEST_FEATURE"), any()))
                .thenReturn(new PageImpl<>(List.of("user1", "user2"), PageRequest.of(0, 20), 2));

        // When/Then
        mockMvc.perform(get("/api/v1/toggles/TEST_FEATURE/whitelist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0]").value("user1"))
                .andExpect(jsonPath("$.content[1]").value("user2"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/toggles/{name}/blacklist should add users")
    void addToBlacklist_shouldAddUsers() throws Exception {
        // Given
        UserListRequest request = new UserListRequest();
        request.setUserIds(List.of("blocked1"));

        doNothing().when(toggleService).addUsersToBlacklist(eq("TEST_FEATURE"), anyList(), any());

        // When/Then
        mockMvc.perform(post("/api/v1/toggles/TEST_FEATURE/blacklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(toggleService).addUsersToBlacklist(eq("TEST_FEATURE"), eq(List.of("blocked1")), any());
    }

    @Test
    @DisplayName("POST /api/v1/toggles/{name}/blacklist/remove should remove users")
    void removeFromBlacklist_shouldRemoveUsers() throws Exception {
        // Given
        UserListRequest request = new UserListRequest();
        request.setUserIds(List.of("blocked1"));

        doNothing().when(toggleService).removeUsersFromBlacklist(eq("TEST_FEATURE"), anyList(), any());

        // When/Then
        mockMvc.perform(post("/api/v1/toggles/TEST_FEATURE/blacklist/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(toggleService).removeUsersFromBlacklist(eq("TEST_FEATURE"), eq(List.of("blocked1")), any());
    }

    @Test
    @DisplayName("GET /api/v1/toggles/{name}/blacklist should return paginated users")
    void getBlacklist_shouldReturnPaginatedUsers() throws Exception {
        // Given
        when(toggleService.getBlacklistedUsers(eq("TEST_FEATURE"), any()))
                .thenReturn(new PageImpl<>(List.of("blocked1"), PageRequest.of(0, 20), 1));

        // When/Then
        mockMvc.perform(get("/api/v1/toggles/TEST_FEATURE/blacklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0]").value("blocked1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/toggles with group filter should return filtered toggles")
    void getAllToggles_withGroupFilter_shouldReturnFilteredToggles() throws Exception {
        // Given
        FeatureToggleDto toggle = FeatureToggleDto.builder()
                .id(1L)
                .featureName("PAYMENT_FEATURE")
                .status(ToggleStatus.ENABLED)
                .groupName("payment")
                .build();
        when(toggleService.getTogglesByGroup("payment")).thenReturn(List.of(toggle));

        // When/Then
        mockMvc.perform(get("/api/v1/toggles").param("group", "payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].featureName").value("PAYMENT_FEATURE"))
                .andExpect(jsonPath("$[0].groupName").value("payment"));
    }

    @Test
    @DisplayName("POST /api/v1/toggles with groupName should create toggle with group")
    void createToggle_withGroup_shouldCreateToggleWithGroup() throws Exception {
        // Given
        CreateFeatureToggleRequest request = new CreateFeatureToggleRequest();
        request.setFeatureName("PAYMENT_WITHDRAW");
        request.setStatus(ToggleStatus.ENABLED);
        request.setGroupName("payment");

        FeatureToggleDto created = FeatureToggleDto.builder()
                .id(1L)
                .featureName("PAYMENT_WITHDRAW")
                .status(ToggleStatus.ENABLED)
                .groupName("payment")
                .build();
        when(toggleService.createToggle(any(), any())).thenReturn(created);

        // When/Then
        mockMvc.perform(post("/api/v1/toggles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupName").value("payment"));
    }

    @Test
    @DisplayName("POST /api/v1/toggles/{name}/schedule should schedule toggle")
    void scheduleToggle_shouldScheduleToggle() throws Exception {
        // Given
        ScheduleToggleRequest request = new ScheduleToggleRequest();
        request.setScheduledStatus(ToggleStatus.DISABLED);
        request.setScheduledAt(Instant.parse("2026-02-01T00:00:00Z"));

        FeatureToggleDto scheduled = FeatureToggleDto.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.ENABLED)
                .scheduledStatus(ToggleStatus.DISABLED)
                .scheduledAt(Instant.parse("2026-02-01T00:00:00Z"))
                .build();
        when(toggleService.scheduleToggle(eq("TEST_FEATURE"), eq(ToggleStatus.DISABLED), any(), any()))
                .thenReturn(scheduled);

        // When/Then
        mockMvc.perform(post("/api/v1/toggles/TEST_FEATURE/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledStatus").value("DISABLED"))
                .andExpect(jsonPath("$.scheduledAt").exists());
    }

    @Test
    @DisplayName("DELETE /api/v1/toggles/{name}/schedule should cancel schedule")
    void cancelSchedule_shouldCancelSchedule() throws Exception {
        // Given
        FeatureToggleDto toggle = FeatureToggleDto.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.ENABLED)
                .scheduledStatus(null)
                .scheduledAt(null)
                .build();
        when(toggleService.cancelSchedule(eq("TEST_FEATURE"), any())).thenReturn(toggle);

        // When/Then
        mockMvc.perform(delete("/api/v1/toggles/TEST_FEATURE/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledStatus").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/toggles with X-Actor header should pass actor to service")
    void createToggle_withActorHeader_shouldPassActorToService() throws Exception {
        // Given
        CreateFeatureToggleRequest request = new CreateFeatureToggleRequest();
        request.setFeatureName("NEW_FEATURE");
        request.setStatus(ToggleStatus.ENABLED);

        FeatureToggleDto created = FeatureToggleDto.builder()
                .id(1L)
                .featureName("NEW_FEATURE")
                .status(ToggleStatus.ENABLED)
                .build();
        when(toggleService.createToggle(any(), eq("admin@example.com"))).thenReturn(created);

        // When/Then
        mockMvc.perform(post("/api/v1/toggles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor", "admin@example.com")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(toggleService).createToggle(any(), eq("admin@example.com"));
    }

    @Test
    @DisplayName("PUT /api/v1/toggles/{name} with X-Actor header should pass actor to service")
    void updateToggle_withActorHeader_shouldPassActorToService() throws Exception {
        // Given
        UpdateFeatureToggleRequest request = new UpdateFeatureToggleRequest();
        request.setStatus(ToggleStatus.DISABLED);

        FeatureToggleDto updated = FeatureToggleDto.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .status(ToggleStatus.DISABLED)
                .build();
        when(toggleService.updateToggle(eq("TEST_FEATURE"), any(), eq("admin@example.com"))).thenReturn(updated);

        // When/Then
        mockMvc.perform(put("/api/v1/toggles/TEST_FEATURE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor", "admin@example.com")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(toggleService).updateToggle(eq("TEST_FEATURE"), any(), eq("admin@example.com"));
    }

    @Test
    @DisplayName("DELETE /api/v1/toggles/{name} with X-Actor header should pass actor to service")
    void deleteToggle_withActorHeader_shouldPassActorToService() throws Exception {
        // Given
        doNothing().when(toggleService).deleteToggle(eq("TEST_FEATURE"), eq("admin@example.com"));

        // When/Then
        mockMvc.perform(delete("/api/v1/toggles/TEST_FEATURE")
                        .header("X-Actor", "admin@example.com"))
                .andExpect(status().isNoContent());

        verify(toggleService).deleteToggle(eq("TEST_FEATURE"), eq("admin@example.com"));
    }
}
