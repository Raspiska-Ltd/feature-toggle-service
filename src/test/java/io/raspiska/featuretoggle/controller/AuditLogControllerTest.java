package io.raspiska.featuretoggle.controller;

import io.raspiska.featuretoggle.dto.AuditLogDto;
import io.raspiska.featuretoggle.entity.AuditLog.AuditAction;
import io.raspiska.featuretoggle.service.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @Test
    @DisplayName("GET /api/v1/audit should return paginated audit logs")
    void getAuditLogs_shouldReturnPaginatedLogs() throws Exception {
        // Given
        AuditLogDto log1 = AuditLogDto.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .action(AuditAction.CREATE)
                .actor("admin@example.com")
                .timestamp(Instant.now())
                .build();
        AuditLogDto log2 = AuditLogDto.builder()
                .id(2L)
                .featureName("TEST_FEATURE")
                .action(AuditAction.UPDATE)
                .actor("admin@example.com")
                .timestamp(Instant.now())
                .build();

        when(auditLogService.getAuditLogs(eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(log1, log2), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp")), 2));

        // When/Then
        mockMvc.perform(get("/api/v1/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].featureName").value("TEST_FEATURE"))
                .andExpect(jsonPath("$.content[0].action").value("CREATE"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/audit with featureName filter should return filtered logs")
    void getAuditLogs_withFeatureNameFilter_shouldReturnFilteredLogs() throws Exception {
        // Given
        AuditLogDto log = AuditLogDto.builder()
                .id(1L)
                .featureName("WITHDRAW")
                .action(AuditAction.UPDATE)
                .actor("admin@example.com")
                .timestamp(Instant.now())
                .build();

        when(auditLogService.getAuditLogs(eq("WITHDRAW"), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        // When/Then
        mockMvc.perform(get("/api/v1/audit").param("featureName", "WITHDRAW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].featureName").value("WITHDRAW"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/audit with actor filter should return filtered logs")
    void getAuditLogs_withActorFilter_shouldReturnFilteredLogs() throws Exception {
        // Given
        AuditLogDto log = AuditLogDto.builder()
                .id(1L)
                .featureName("TEST_FEATURE")
                .action(AuditAction.CREATE)
                .actor("john@example.com")
                .timestamp(Instant.now())
                .build();

        when(auditLogService.getAuditLogs(eq(null), eq("john@example.com"), any()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        // When/Then
        mockMvc.perform(get("/api/v1/audit").param("actor", "john@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actor").value("john@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/audit with both filters should return filtered logs")
    void getAuditLogs_withBothFilters_shouldReturnFilteredLogs() throws Exception {
        // Given
        AuditLogDto log = AuditLogDto.builder()
                .id(1L)
                .featureName("WITHDRAW")
                .action(AuditAction.UPDATE)
                .actor("admin@example.com")
                .timestamp(Instant.now())
                .build();

        when(auditLogService.getAuditLogs(eq("WITHDRAW"), eq("admin@example.com"), any()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        // When/Then
        mockMvc.perform(get("/api/v1/audit")
                        .param("featureName", "WITHDRAW")
                        .param("actor", "admin@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].featureName").value("WITHDRAW"))
                .andExpect(jsonPath("$.content[0].actor").value("admin@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/audit with pagination should return correct page")
    void getAuditLogs_withPagination_shouldReturnCorrectPage() throws Exception {
        // Given
        AuditLogDto log = AuditLogDto.builder()
                .id(3L)
                .featureName("TEST_FEATURE")
                .action(AuditAction.UPDATE)
                .timestamp(Instant.now())
                .build();

        when(auditLogService.getAuditLogs(eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(1, 10), 25));

        // When/Then
        mockMvc.perform(get("/api/v1/audit")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(1));
    }
}
