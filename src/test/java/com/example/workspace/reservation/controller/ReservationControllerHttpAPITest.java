package com.example.workspace.reservation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("ReservationController - HTTP API Endpoints")
class ReservationControllerHttpAPITest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "/api/v1/reservations";

    // Helper method: create a valid POST request body
    private String createRequestBody(String workspaceId, String userId,
                                     String startTime, String endTime) {
        return String.format("""
            {
                "workspaceId": "%s",
                "userId": "%s",
                "startTime": "%s",
                "endTime": "%s"
            }
            """, workspaceId, userId, startTime, endTime);
    }

    // Helper method: extract UUID from response body
    private String extractIdFromResponse(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        return json.get("id").asText();
    }

    // ===== Group A: POST - Successful Creation (201 Created) =====

    @Test
    @DisplayName("POST /api/v1/reservations with valid data returns 201 Created")
    void testCreateReservationWithValidDataReturns201() throws Exception {
        String requestBody = createRequestBody(
            "workspace-1",
            "user-1",
            "2025-09-07T10:00:00Z",
            "2025-09-07T11:00:00Z"
        );

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
    }

    @Test
    @DisplayName("POST creates reservation with id, status=ACTIVE, and all fields in response")
    void testCreateReservationResponseHasCorrectStructure() throws Exception {
        String requestBody = createRequestBody(
            "workspace-1",
            "user-1",
            "2025-09-07T10:00:00Z",
            "2025-09-07T11:00:00Z"
        );

        MvcResult result = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        // Verify all fields present
        assertThat(json.has("id")).isTrue();
        assertThat(json.has("workspaceId")).isTrue();
        assertThat(json.has("userId")).isTrue();
        assertThat(json.has("startTime")).isTrue();
        assertThat(json.has("endTime")).isTrue();
        assertThat(json.has("status")).isTrue();

        // Verify correct values
        assertThat(json.get("workspaceId").asText()).isEqualTo("workspace-1");
        assertThat(json.get("userId").asText()).isEqualTo("user-1");
        assertThat(json.get("startTime").asText()).isEqualTo("2025-09-07T10:00:00Z");
        assertThat(json.get("endTime").asText()).isEqualTo("2025-09-07T11:00:00Z");
        assertThat(json.get("status").asText()).isEqualTo("ACTIVE");

        // Verify UUID format
        String id = json.get("id").asText();
        assertThat(id).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    @DisplayName("POST generates unique UUID for each reservation")
    void testCreateReservationGeneratesUniqueIds() throws Exception {
        String requestBody1 = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");
        String requestBody2 = createRequestBody("workspace-2", "user-2",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        MvcResult result1 = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody1))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody2))
                .andExpect(status().isCreated())
                .andReturn();

        String id1 = extractIdFromResponse(result1);
        String id2 = extractIdFromResponse(result2);

        assertThat(id1).isNotEqualTo(id2);
    }

    // ===== Group B: POST - Conflict Detection (409 Conflict) =====

    @Test
    @DisplayName("POST with overlapping times for same workspace returns 409 Conflict")
    void testCreateReservationWithOverlapOnSameWorkspaceReturns409() throws Exception {
        String requestBody1 = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        // Create first reservation successfully
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody1))
                .andExpect(status().isCreated())
                .andReturn();

        // Try to create overlapping reservation on same workspace
        String requestBody2 = createRequestBody("workspace-1", "user-2",
            "2025-09-07T10:30:00Z", "2025-09-07T11:30:00Z");

        MvcResult result = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody2))
                .andExpect(status().isConflict())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.get("error").asText()).isEqualTo("CONFLICT");
        assertThat(json.has("details")).isTrue();
        assertThat(json.get("details").asText()).isNotBlank();
    }

    @Test
    @DisplayName("POST with adjacent (non-overlapping) times returns 201, not 409")
    void testCreateReservationWithAdjacentTimesReturns201() throws Exception {
        String requestBody1 = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody1))
                .andExpect(status().isCreated())
                .andReturn();

        // Create adjacent (non-overlapping) reservation
        String requestBody2 = createRequestBody("workspace-1", "user-2",
            "2025-09-07T11:00:00Z", "2025-09-07T12:00:00Z");

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody2))
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    @DisplayName("POST with same times on different workspaces returns 201 for both")
    void testCreateReservationDifferentWorkspacesDoNotConflict() throws Exception {
        String requestBody1 = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody1))
                .andExpect(status().isCreated())
                .andReturn();

        // Same time range but different workspace should succeed
        String requestBody2 = createRequestBody("workspace-2", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody2))
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    @DisplayName("POST on cancelled reservation time range returns 201, not 409")
    void testCreateReservationDoesNotConflictWithCancelledReservation() throws Exception {
        String requestBody1 = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        // Create first reservation
        MvcResult result1 = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody1))
                .andExpect(status().isCreated())
                .andReturn();

        String id1 = extractIdFromResponse(result1);

        // Cancel the first reservation
        mockMvc.perform(delete(BASE_URL + "/" + id1))
                .andExpect(status().isNoContent())
                .andReturn();

        // Try to create overlapping reservation on same workspace
        // This should succeed because first reservation is cancelled
        String requestBody2 = createRequestBody("workspace-1", "user-2",
            "2025-09-07T10:30:00Z", "2025-09-07T11:30:00Z");

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody2))
                .andExpect(status().isCreated())
                .andReturn();
    }

    // ===== Group C: GET - Successful Retrieval (200 OK) =====

    @Test
    @DisplayName("GET /api/v1/reservations/{id} with valid id returns 200 OK")
    void testGetReservationReturns200() throws Exception {
        String requestBody = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        // Create reservation
        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String id = extractIdFromResponse(createResult);

        // Get the created reservation
        mockMvc.perform(get(BASE_URL + "/" + id)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
    }

    @Test
    @DisplayName("GET response contains all reservation fields matching created data")
    void testGetReservationResponseHasAllFields() throws Exception {
        String requestBody = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String id = extractIdFromResponse(createResult);

        MvcResult getResult = mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = getResult.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.get("id").asText()).isEqualTo(id);
        assertThat(json.get("workspaceId").asText()).isEqualTo("workspace-1");
        assertThat(json.get("userId").asText()).isEqualTo("user-1");
        assertThat(json.get("startTime").asText()).isEqualTo("2025-09-07T10:00:00Z");
        assertThat(json.get("endTime").asText()).isEqualTo("2025-09-07T11:00:00Z");
        assertThat(json.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("GET cancelled reservation returns 200 OK with status=CANCELLED")
    void testGetCancelledReservationReturns200() throws Exception {
        String requestBody = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        // Create reservation
        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String id = extractIdFromResponse(createResult);

        // Cancel the reservation
        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent())
                .andReturn();

        // Get the cancelled reservation
        MvcResult getResult = mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = getResult.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.get("status").asText()).isEqualTo("CANCELLED");
    }

    // ===== Group D: DELETE - Successful Cancellation (204 No Content) =====

    @Test
    @DisplayName("DELETE /api/v1/reservations/{id} with active reservation returns 204 No Content")
    void testDeleteActiveReservationReturns204() throws Exception {
        String requestBody = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        // Create reservation
        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String id = extractIdFromResponse(createResult);

        // Delete the reservation
        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent())
                .andReturn();
    }

    @Test
    @DisplayName("DELETE response has empty body")
    void testDeleteReservationResponseIsEmpty() throws Exception {
        String requestBody = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String id = extractIdFromResponse(createResult);

        MvcResult deleteResult = mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent())
                .andReturn();

        String responseBody = deleteResult.getResponse().getContentAsString();
        assertThat(responseBody).isEmpty();
    }

    @Test
    @DisplayName("GET after DELETE shows status=CANCELLED")
    void testGetAfterDeleteShowsCancelledStatus() throws Exception {
        String requestBody = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String id = extractIdFromResponse(createResult);

        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent())
                .andReturn();

        MvcResult getResult = mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = getResult.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("DELETE on already-cancelled reservation returns 204 (idempotent)")
    void testDeleteCancelledReservationReturns204() throws Exception {
        String requestBody = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String id = extractIdFromResponse(createResult);

        // Delete once
        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent())
                .andReturn();

        // Delete again (should still succeed with 204)
        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent())
                .andReturn();
    }

    // ===== Group E: Concurrency at HTTP Layer =====

    @Test
    @DisplayName("Concurrent POST requests for overlapping times: one 201, one 409")
    void testConcurrentOverlappingCreateRequestsRaceCondition() throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch endSignal = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try {
                    startSignal.await();

                    String requestBody = createRequestBody("workspace-race", "user-" + Thread.currentThread().getId(),
                        "2025-09-07T15:00:00Z", "2025-09-07T16:00:00Z");

                    MvcResult result = mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                            .andReturn();

                    if (result.getResponse().getStatus() == 201) {
                        successCount.incrementAndGet();
                    } else if (result.getResponse().getStatus() == 409) {
                        conflictCount.incrementAndGet();
                    }

                    endSignal.countDown();
                } catch (Exception e) {
                    exceptions.add(e);
                    endSignal.countDown();
                }
            }).start();
        }

        // Signal threads to start simultaneously
        startSignal.countDown();

        // Wait for both threads to complete
        boolean completed = endSignal.await(10, TimeUnit.SECONDS);

        // Verify no exceptions occurred
        assertThat(exceptions).isEmpty();

        // Verify exactly one succeeded and one got conflict
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrent POST for adjacent (non-overlapping) times: both 201")
    void testConcurrentAdjacentCreateRequestsBothSucceed() throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch endSignal = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // Create first reservation to establish time range
        String requestBody0 = createRequestBody("workspace-adj", "user-0",
            "2025-09-07T14:00:00Z", "2025-09-07T15:00:00Z");
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody0))
                .andExpect(status().isCreated());

        for (int i = 0; i < 2; i++) {
            final int threadNum = i;
            new Thread(() -> {
                try {
                    startSignal.await();

                    String startTime = threadNum == 0 ? "2025-09-07T15:00:00Z" : "2025-09-07T16:00:00Z";
                    String endTime = threadNum == 0 ? "2025-09-07T16:00:00Z" : "2025-09-07T17:00:00Z";

                    String requestBody = createRequestBody("workspace-adj", "user-adj-" + threadNum,
                        startTime, endTime);

                    MvcResult result = mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                            .andReturn();

                    if (result.getResponse().getStatus() == 201) {
                        successCount.incrementAndGet();
                    }

                    endSignal.countDown();
                } catch (Exception e) {
                    exceptions.add(e);
                    endSignal.countDown();
                }
            }).start();
        }

        startSignal.countDown();
        boolean completed = endSignal.await(10, TimeUnit.SECONDS);

        assertThat(exceptions).isEmpty();
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(2);  // Both should succeed
    }

    @Test
    @DisplayName("Concurrent POST on different workspaces with same times: both 201")
    void testConcurrentDifferentWorkspacesCreatesBoth() throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch endSignal = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < 2; i++) {
            final int threadNum = i;
            new Thread(() -> {
                try {
                    startSignal.await();

                    String requestBody = createRequestBody("workspace-" + threadNum, "user-" + threadNum,
                        "2025-09-07T17:00:00Z", "2025-09-07T18:00:00Z");

                    MvcResult result = mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                            .andReturn();

                    if (result.getResponse().getStatus() == 201) {
                        successCount.incrementAndGet();
                    }

                    endSignal.countDown();
                } catch (Exception e) {
                    exceptions.add(e);
                    endSignal.countDown();
                }
            }).start();
        }

        startSignal.countDown();
        boolean completed = endSignal.await(10, TimeUnit.SECONDS);

        assertThat(exceptions).isEmpty();
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(2);  // Both should succeed on different workspaces
    }

    // ===== Group F: Content-Type and JSON Serialization =====

    @Test
    @DisplayName("POST response includes Content-Type: application/json header")
    void testPostResponseHasContentType() throws Exception {
        String requestBody = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
    }

    @Test
    @DisplayName("GET response includes Content-Type: application/json header")
    void testGetResponseHasContentType() throws Exception {
        String requestBody = createRequestBody("workspace-1", "user-1",
            "2025-09-07T10:00:00Z", "2025-09-07T11:00:00Z");

        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String id = extractIdFromResponse(createResult);

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
    }

    @Test
    @DisplayName("Error responses (400, 404, 409) include Content-Type: application/json")
    void testErrorResponsesHaveJsonContentType() throws Exception {
        // Test 400 Bad Request
        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Test 404 Not Found
        mockMvc.perform(get(BASE_URL + "/12345678-1234-1234-1234-123456789012"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
    }

    // ===== Group G: Response Body Structure Validation =====

    @Test
    @DisplayName("Error responses have error and details fields")
    void testErrorResponseStructure() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.has("error")).isTrue();
        assertThat(json.has("details")).isTrue();
        assertThat(json.get("error").isTextual()).isTrue();
        assertThat(json.get("details").isTextual()).isTrue();
    }

    @Test
    @DisplayName("Request and response timestamps match in ISO 8601 UTC format")
    void testTimestampFormatPreservation() throws Exception {
        String startTime = "2025-09-07T14:30:45Z";
        String endTime = "2025-09-07T15:45:30Z";

        String requestBody = createRequestBody("workspace-1", "user-1", startTime, endTime);

        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.get("startTime").asText()).isEqualTo(startTime);
        assertThat(json.get("endTime").asText()).isEqualTo(endTime);
    }
}

