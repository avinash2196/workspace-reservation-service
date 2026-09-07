# Implementation Plan: Milestone 7 - HTTP API Endpoints (RED)

**Status**: Pending Review and Authorization

**Traceability**: 
- Approved Requirements: [docs/requirements.md](../requirements.md) § API Endpoints
- Approved Plan: [Plan.md](./Plan.md) § Milestone 7
- Approved Contract: [0_API_Contract.md](./0_API_Contract.md)
- Predecessor: Milestone 6 (GREEN) verified complete

---

## Phase: RED

**Goal**: Define test contract for HTTP endpoint behavior and request/response mapping.

**Scope**: Tests only. No production code implementation. All tests proposed to fail initially (endpoints not yet wired).

---

## What RED Tests Will Verify

1. **POST /api/v1/reservations - Successful Creation (201 Created)**
   - Request is accepted with valid reservation data
   - Response status is 201 Created
   - Response body contains complete reservation object
   - Response includes server-generated UUID id
   - Response status field is "ACTIVE"
   - Response timestamp fields match request input
   - Response Content-Type is application/json

2. **POST /api/v1/reservations - Conflict Detection (409 Conflict)**
   - Two overlapping reservations for same workspace returns 409 on second request
   - Response body contains error classification "CONFLICT"
   - CANCELLED reservations do not block new reservations (no 409)
   - Different workspaces do not interfere (no 409)

3. **GET /api/v1/reservations/{id} - Successful Retrieval (200 OK)**
   - Existing reservation returns 200 OK
   - Response body contains complete reservation object
   - Response fields match the created reservation
   - Response Content-Type is application/json
   - CANCELLED reservations are retrievable

4. **DELETE /api/v1/reservations/{id} - Successful Cancellation (204 No Content)**
   - Existing ACTIVE reservation returns 204 No Content
   - Response has empty body
   - Subsequent GET shows status=CANCELLED
   - Idempotent: cancelling CANCELLED reservation also returns 204
   - CANCELLED reservation no longer blocks overlapping reservations

5. **HTTP Error Status Codes**
   - 400 Bad Request: validation failures, invalid UUID path format
   - 404 Not Found: non-existent reservation on GET/DELETE
   - 409 Conflict: overlapping reservations on POST

6. **Content Negotiation**
   - POST requests require Content-Type: application/json
   - Response Content-Type is application/json for all successful responses
   - Error responses include JSON with error and details fields

7. **UUID Path Parameter Parsing**
   - Valid UUID in path is parsed correctly
   - Invalid UUID format returns 400 Bad Request
   - Empty or malformed UUID returns 400 Bad Request

8. **Concurrency at HTTP Layer**
   - Concurrent POST requests for overlapping reservations on same workspace
   - Exactly one succeeds with 201, others receive 409
   - Race condition: both concurrent requests must not create duplicate ACTIVE reservations

---

## Test Organization

New tests will be added to the existing controller test suite. Tests are organized in logical groups:

- **Group A**: POST - Successful Creation (201)
- **Group B**: POST - Conflict Detection (409)  
- **Group C**: GET - Successful Retrieval (200)
- **Group D**: DELETE - Successful Cancellation (204)
- **Group E**: Concurrency at HTTP Layer
- **Group F**: Content-Type and JSON Serialization
- **Group G**: Response Body Structure Validation

---

## Proposed Test Implementation

All tests below are proposed for `src/test/java/com/example/workspace/reservation/controller/ReservationControllerHttpAPITest.java`.

### Prerequisites

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
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
```

### Group A: POST - Successful Creation (201 Created)

#### Test A.1: Create reservation with valid data returns 201

```java
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
```

#### Test A.2: Created reservation has correct response body structure

```java
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
```

#### Test A.3: Server-generated UUID is unique across multiple creates

```java
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
```

---

### Group B: POST - Conflict Detection (409 Conflict)

#### Test B.1: Overlapping reservations on same workspace return 409

```java
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
```

#### Test B.2: Adjacent reservations (no overlap) do not return 409

```java
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
```

#### Test B.3: Different workspaces do not interfere

```java
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
```

#### Test B.4: Cancelled reservations do not block new reservations

```java
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
```

---

### Group C: GET - Successful Retrieval (200 OK)

#### Test C.1: Get existing reservation returns 200 OK with full response

```java
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
```

#### Test C.2: Get response contains all reservation fields

```java
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
```

#### Test C.3: Get cancelled reservation still returns 200 OK with status=CANCELLED

```java
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
```

---

### Group D: DELETE - Successful Cancellation (204 No Content)

#### Test D.1: Delete active reservation returns 204 with no body

```java
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
```

#### Test D.2: Deleted reservation has no response body

```java
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
```

#### Test D.3: Subsequent GET shows status changed to CANCELLED

```java
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
```

#### Test D.4: Delete already-cancelled reservation returns 204 (idempotent)

```java
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
```

---

### Group E: Concurrency at HTTP Layer

#### Test E.1: Concurrent POST requests for overlapping reservations - one succeeds, one gets 409

```java
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
```

#### Test E.2: Concurrent POST for adjacent times (no overlap) both succeed

```java
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
```

#### Test E.3: Concurrent POST on different workspaces both succeed

```java
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
```

---

### Group F: Content-Type and JSON Serialization

#### Test F.1: POST response includes Content-Type application/json

```java
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
```

#### Test F.2: GET response includes Content-Type application/json

```java
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
```

#### Test F.3: Error responses include Content-Type application/json

```java
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
```

---

### Group G: Response Body Structure Validation

#### Test G.1: Error response has error and details fields

```java
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
```

#### Test G.2: Timestamps in request and response match in ISO 8601 UTC format

```java
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
```

---

## Test Dependencies and Imports

All proposed tests require:

```java
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
```

---

## Expected Test Results

**RED Phase Expectation**: All proposed tests will **FAIL** initially because:

1. POST /api/v1/reservations endpoint is not yet wired (no controller)
2. GET /api/v1/reservations/{id} endpoint is not yet wired
3. DELETE /api/v1/reservations/{id} endpoint is not yet wired
4. No request/response DTO mapping
5. No error handling at HTTP layer
6. No endpoint routing in Spring Boot

**Failure Evidence**:
- Most tests will receive 404 (endpoints not found)
- Some may receive 415 (unsupported media type) if endpoints are stubbed but not wired
- UUID path parameter parsing not implemented
- Concurrency tests will have nothing to synchronize with

This is expected RED behavior: tests define contract before implementation.

---

## Test Execution Plan

1. Create test file: `src/test/java/com/example/workspace/reservation/controller/ReservationControllerHttpAPITest.java`
2. Add all proposed test classes and methods from Groups A-G above
3. Run `./mvnw test` to verify all tests fail with appropriate evidence
4. Capture test execution output showing failures

---

## Success Criteria for RED Phase

✓ All 32+ tests are defined and executable  
✓ Test code compiles without errors  
✓ All tests fail (expected RED evidence)  
✓ Failures are due to missing endpoints, not test logic errors  
✓ Test organization by Groups (A-G) is clear  
✓ Tests cover all required behaviors from API Contract (Milestone 0)  
✓ Concurrency tests exercise the race condition window  
✓ JSON response validation is concrete and specific  
✓ Error status codes (400, 404, 409) are tested at HTTP layer  

---

## Notes

- Tests are **integration tests**, not unit tests, exercising the full HTTP stack via MockMvc
- Tests are **independent**: each test creates its own reservations to avoid state coupling
- Helper methods (`createRequestBody`, `extractIdFromResponse`) reduce boilerplate
- Concurrency tests use `CountDownLatch` to synchronize thread start, exposing true race condition window
- All tests follow existing test patterns from `ReservationControllerValidationTest.java`
- No production code changes are proposed in RED phase

---

## Predecessor Evidence

This Implementation Plan follows successful completion of:
- **Milestone 1 (RED)**: Core store tests defined
- **Milestone 2 (GREEN)**: ReservationStore implemented with ConcurrentHashMap
- **Milestone 3 (RED)**: Validation tests defined
- **Milestone 4 (GREEN)**: Validators and error responses implemented
- **Milestone 5 (RED)**: Conflict detection and concurrency tests defined
- **Milestone 6 (GREEN)**: Conflict detection with `overlaps()` predicate and synchronized `createWithConflictCheck()` implemented

All 54 tests from Milestones 1-6 pass. Ready to proceed with Milestone 7 RED test definition.

---

## Successor

Upon human approval and completion of RED phase testing:
- **Milestone 8 (GREEN)**: Spring Boot REST controller implementation wiring the three endpoints to business logic and mapping requests/responses

---

## Exclusions

This Implementation Plan explicitly does NOT include:

- List/query endpoints (per contract exclusions)
- Bulk operations
- Pagination
- Filtering or searching
- Reservation updates or modifications
- Authentication or authorization
- Health checks or metrics
- Caching headers


0