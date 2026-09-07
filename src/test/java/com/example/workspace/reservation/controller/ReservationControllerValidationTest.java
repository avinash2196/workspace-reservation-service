package com.example.workspace.reservation.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("ReservationController - Validation and Error Handling")
class ReservationControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "/api/v1/reservations";

    // Group A: POST /api/v1/reservations - Required Field Validation (400 Bad Request)

    @Test
    @DisplayName("POST should return 400 Bad Request when workspaceId is missing")
    void testCreateReservationMissingWorkspaceIdReturns400() throws Exception {
        String requestBody = """
                {
                    "userId": "user-1",
                    "startTime": "2025-09-07T10:00:00Z",
                    "endTime": "2025-09-07T11:00:00Z"
                }
                """;

        MvcResult result = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.has("error")).isTrue();
        assertThat(json.has("details")).isTrue();
        assertThat(json.get("error").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(json.get("details").asText()).isNotBlank();
    }

    @Test
    @DisplayName("POST should return 400 Bad Request when userId is missing")
    void testCreateReservationMissingUserIdReturns400() throws Exception {
        String requestBody = """
                {
                    "workspaceId": "ws-1",
                    "startTime": "2025-09-07T10:00:00Z",
                    "endTime": "2025-09-07T11:00:00Z"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isString())
                .andReturn();
    }

    @Test
    @DisplayName("POST should return 400 Bad Request when startTime is missing")
    void testCreateReservationMissingStartTimeReturns400() throws Exception {
        String requestBody = """
                {
                    "workspaceId": "ws-1",
                    "userId": "user-1",
                    "endTime": "2025-09-07T11:00:00Z"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").exists());
    }

    @Test
    @DisplayName("POST should return 400 Bad Request when endTime is missing")
    void testCreateReservationMissingEndTimeReturns400() throws Exception {
        String requestBody = """
                {
                    "workspaceId": "ws-1",
                    "userId": "user-1",
                    "startTime": "2025-09-07T10:00:00Z"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST should return 400 Bad Request when multiple required fields are missing")
    void testCreateReservationMissingMultipleFieldsReturns400() throws Exception {
        String requestBody = """
                {
                    "workspaceId": "ws-1"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST should return 400 Bad Request when request body is empty")
    void testCreateReservationEmptyRequestBodyReturns400() throws Exception {
        String requestBody = "{}";

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // Group B: POST /api/v1/reservations - Time Range Validation (400 Bad Request)

    @Test
    @DisplayName("POST should return 400 Bad Request when startTime equals endTime")
    void testCreateReservationStartTimeEqualsEndTimeReturns400() throws Exception {
        String requestBody = """
                {
                    "workspaceId": "ws-1",
                    "userId": "user-1",
                    "startTime": "2025-09-07T10:00:00Z",
                    "endTime": "2025-09-07T10:00:00Z"
                }
                """;

        MvcResult result = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.get("error").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(json.get("details").asText()).containsIgnoringCase("before");
    }

    @Test
    @DisplayName("POST should return 400 Bad Request when startTime is after endTime")
    void testCreateReservationStartTimeAfterEndTimeReturns400() throws Exception {
        String requestBody = """
                {
                    "workspaceId": "ws-1",
                    "userId": "user-1",
                    "startTime": "2025-09-07T11:00:00Z",
                    "endTime": "2025-09-07T10:00:00Z"
                }
                """;

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // Group C: POST /api/v1/reservations - Error Response Structure

    @Test
    @DisplayName("POST validation error response has correct structure")
    void testCreateReservationValidationErrorHasCorrectErrorResponseStructure() throws Exception {
        String requestBody = """
                {
                    "userId": "user-1",
                    "startTime": "2025-09-07T10:00:00Z",
                    "endTime": "2025-09-07T11:00:00Z"
                }
                """;

        MvcResult result = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        // Verify structure
        assertThat(json.has("error")).isTrue();
        assertThat(json.has("details")).isTrue();
        assertThat(json.get("error").isTextual()).isTrue();
        assertThat(json.get("details").isTextual()).isTrue();

        // Verify values
        assertThat(json.get("error").asText()).isNotBlank();
        assertThat(json.get("details").asText()).isNotBlank();
    }

    // Group D: GET /api/v1/reservations/{id} - 404 Not Found

    @Test
    @DisplayName("GET should return 404 Not Found for non-existent reservation id")
    void testGetReservationNonExistentIdReturns404() throws Exception {
        String nonExistentId = "12345678-1234-1234-1234-123456789012";

        MvcResult result = mockMvc.perform(get(BASE_URL + "/" + nonExistentId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(json.get("details").asText()).isNotBlank();
        assertThat(json.get("details").asText()).contains(nonExistentId);
    }

    @Test
    @DisplayName("GET should return 400 Bad Request for invalid UUID format")
    void testGetReservationInvalidUuidFormatReturns400() throws Exception {
        String invalidId = "not-a-uuid";

        mockMvc.perform(get(BASE_URL + "/" + invalidId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // Group E: DELETE /api/v1/reservations/{id} - 404 Not Found

    @Test
    @DisplayName("DELETE should return 404 Not Found for non-existent reservation id")
    void testCancelReservationNonExistentIdReturns404() throws Exception {
        String nonExistentId = "12345678-1234-1234-1234-123456789012";

        MvcResult result = mockMvc.perform(delete(BASE_URL + "/" + nonExistentId))
                .andExpect(status().isNotFound())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(json.get("details").asText()).isNotBlank();
        assertThat(json.get("details").asText()).contains(nonExistentId);
    }

    @Test
    @DisplayName("DELETE should return 400 Bad Request for invalid UUID format")
    void testCancelReservationInvalidUuidFormatReturns400() throws Exception {
        String invalidId = "invalid-uuid";

        mockMvc.perform(delete(BASE_URL + "/" + invalidId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}

