package io.raspiska.featuretoggle.controller;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleEntityNotFound should return 404 with error message")
    void handleEntityNotFound_shouldReturn404() {
        // Given
        EntityNotFoundException ex = new EntityNotFoundException("Feature toggle not found: TEST_FEATURE");

        // When
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleEntityNotFound(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Feature toggle not found: TEST_FEATURE");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleIllegalArgument should return 400 with error message")
    void handleIllegalArgument_shouldReturn400() {
        // Given
        IllegalArgumentException ex = new IllegalArgumentException("Feature toggle already exists");

        // When
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleIllegalArgument(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Feature toggle already exists");
    }

    @Test
    @DisplayName("handleValidationErrors should return 400 with field errors")
    void handleValidationErrors_shouldReturn400WithFieldErrors() {
        // Given
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "featureName", "must not be blank");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(400);
        assertThat(response.getBody().get("errors")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().get("errors");
        assertThat(errors.get("featureName")).isEqualTo("must not be blank");
    }

    @Test
    @DisplayName("handleGenericException should return 500 with generic message")
    void handleGenericException_shouldReturn500() {
        // Given
        Exception ex = new RuntimeException("Something went wrong");

        // When
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGenericException(ex);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("ErrorResponse record should have correct values")
    void errorResponse_shouldHaveCorrectValues() {
        // Given/When
        GlobalExceptionHandler.ErrorResponse response = new GlobalExceptionHandler.ErrorResponse(
                404, "Not found", java.time.Instant.now());

        // Then
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.message()).isEqualTo("Not found");
        assertThat(response.timestamp()).isNotNull();
    }
}
