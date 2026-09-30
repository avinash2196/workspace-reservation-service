# Implementation Plan: Milestone 3 - Validation and Error Handling - RED

**Milestone**: 3  
**Phase**: RED  
**Title**: Validation and Error Handling - RED  
**Status**: Ready for Human Review  
**Not Approved**: This plan requires human review before authorization to proceed with test implementation.

---

## Traceability

**Approved Baseline**:
- docs/requirements.md § Create Reservation (Validation Rules), § Get Reservation, § Cancel Reservation
- docs/.ai/Plan.md § Milestone 3: Validation and Error Handling - RED
- docs/.ai/0_API_Contract.md § Error Responses, Validation

**This Plan Addresses**: Test contract for HTTP request validation, error response structures, and HTTP status codes for 400, 404 errors at the API layer.

---

## Current Repository State

**Completed (Milestones 1–2)**:
- Domain layer: `Reservation.java`, `ReservationStatus.java` (immutable, fully tested)
- Store layer: `ReservationStore.java` with thread-safe ConcurrentHashMap (fully tested, 20 passing tests)
- Test layer: `ReservationTest.java`, `ReservationStoreTest.java` (all pass)
- Build: `pom.xml` configured for Maven, Java 17, Spring Boot 3, JUnit 5

**Incomplete (Blocking Milestone 3)**:
- Spring Boot HTTP endpoint controllers (no @RestController yet)
- Request/response DTOs for HTTP transport
- Validation layer (no validator classes)
- Error response handlers (no @ExceptionHandler or global error responses)
- HTTP integration tests (no test endpoints)

**Missing Files to Create**:
- HTTP integration test class with validation and error handling tests
- DTO classes for request/response (will be created in GREEN phase, but testing-focused structure visible in RED)

---

## Milestone 3 Scope: Validation and Error Handling - RED

**Goal**: Define test contract for HTTP request validation and error response handling.

**Success Criteria**:
- All tests compile and are runnable via `./mvnw test`
- All tests fail or are skipped initially (no HTTP endpoints or validation logic implemented yet)
- Tests verify HTTP status codes and error response JSON structure from Milestone 0 API Contract
- Tests are integration-level (test HTTP transport, not just domain logic)
- Tests use Spring Boot test framework (MockMvc or TestRestTemplate)

**Exclusions**:
- No endpoint implementation (controllers, routes)
- No request/response DTO classes (those are production code for GREEN)
- No validation logic implementation
- No conflict detection (that is Milestone 5–6)
- No HTTP endpoint integration beyond validation and error scenarios
- No concurrency tests at HTTP layer (those are Milestone 7–8)

---

## Files to Create / Modify

### 1. `src/test/java/com/example/workspace/reservation/controller/ReservationControllerValidationTest.java`

**Status**: To Create  
**Priority**: Critical (RED phase artifact)  
**Content**: Integration tests for HTTP validation and error handling.

**Test Framework**:
- JUnit 5 (from spring-boot-starter-test)
- Spring Boot test annotation: `@SpringBootTest` with `webEnvironment = RANDOM_PORT` (or MockMvc via `@AutoConfigureMockMvc`)
- REST client: MockMvc or TestRestTemplate
- JSON parsing: Jackson (included in Spring Boot)
- Assertions: AssertJ

**Test Endpoints Covered** (RED phase focuses on validation/error, not success paths):

#### Group A: POST /api/v1/reservations - Required Field Validation (400 Bad Request)

1. **test_createReservation_missingWorkspaceId_returns400**
   - POST request body: `{"userId": "user-1", "startTime": "2025-09-07T10:00:00Z", "endTime": "2025-09-07T11:00:00Z"}`
   - Expected: 400 Bad Request with error response
   - Verify: `error` field and `details` field present in response

2. **test_createReservation_missingUserId_returns400**
   - POST request body: `{"workspaceId": "ws-1", "startTime": "2025-09-07T10:00:00Z", "endTime": "2025-09-07T11:00:00Z"}`
   - Expected: 400 Bad Request with error response
   - Verify: `error` = "VALIDATION_ERROR", `details` contains description

3. **test_createReservation_missingStartTime_returns400**
   - POST request body: `{"workspaceId": "ws-1", "userId": "user-1", "endTime": "2025-09-07T11:00:00Z"}`
   - Expected: 400 Bad Request

4. **test_createReservation_missingEndTime_returns400**
   - POST request body: `{"workspaceId": "ws-1", "userId": "user-1", "startTime": "2025-09-07T10:00:00Z"}`
   - Expected: 400 Bad Request

5. **test_createReservation_missingMultipleFields_returns400**
   - POST request body: `{"workspaceId": "ws-1"}` (userId, startTime, endTime all missing)
   - Expected: 400 Bad Request

6. **test_createReservation_emptyRequestBody_returns400**
   - POST request body: `{}`
   - Expected: 400 Bad Request

#### Group B: POST /api/v1/reservations - Time Range Validation (400 Bad Request)

7. **test_createReservation_startTimeEqualsEndTime_returns400**
   - POST request body: `{"workspaceId": "ws-1", "userId": "user-1", "startTime": "2025-09-07T10:00:00Z", "endTime": "2025-09-07T10:00:00Z"}`
   - Expected: 400 Bad Request
   - Verify: `details` indicates startTime must be before endTime

8. **test_createReservation_startTimeAfterEndTime_returns400**
   - POST request body: `{"workspaceId": "ws-1", "userId": "user-1", "startTime": "2025-09-07T11:00:00Z", "endTime": "2025-09-07T10:00:00Z"}`
   - Expected: 400 Bad Request

#### Group C: POST /api/v1/reservations - Error Response Structure

9. **test_createReservation_validationError_hasCorrectErrorResponseStructure**
   - POST request body missing required field
   - Expected: 400 Bad Request
   - Verify response JSON structure:
     - Contains `error` field (string)
     - Contains `details` field (string, descriptive)
     - No other unexpected fields (optional: verify exact field count)
     - Content-Type: application/json
   - Example response:
     ```json
     {
       "error": "VALIDATION_ERROR",
       "details": "Field 'workspaceId' is required"
     }
     ```

#### Group D: GET /api/v1/reservations/{id} - 404 Not Found

10. **test_getReservation_nonExistentId_returns404**
    - GET `/api/v1/reservations/{uuid}` where uuid does not exist in store
    - Expected: 404 Not Found
    - Verify response JSON structure:
      - Contains `error` field = "NOT_FOUND"
      - Contains `details` field describing reservation not found
      - Content-Type: application/json
    - Example response:
      ```json
      {
        "error": "NOT_FOUND",
        "details": "Reservation with id 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx' not found"
      }
      ```

11. **test_getReservation_invalidUuidFormat_returns400**
    - GET `/api/v1/reservations/invalid-uuid-format`
    - Expected: 400 Bad Request (invalid path parameter format)
    - Verify: Error response structure present

#### Group E: DELETE /api/v1/reservations/{id} - 404 Not Found

12. **test_cancelReservation_nonExistentId_returns404**
    - DELETE `/api/v1/reservations/{uuid}` where uuid does not exist
    - Expected: 404 Not Found
    - Verify response JSON structure:
      - Contains `error` field = "NOT_FOUND"
      - Contains `details` field describing reservation not found
    - Example response:
      ```json
      {
        "error": "NOT_FOUND",
        "details": "Reservation with id 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx' not found"
      }
      ```

13. **test_cancelReservation_invalidUuidFormat_returns400**
    - DELETE `/api/v1/reservations/invalid-uuid`
    - Expected: 400 Bad Request (invalid path parameter)
    - Verify: Error response structure

---

## Ordered Implementation Changes

**Phase**: RED (Tests Only)

1. **Create HTTP integration test class**
   - `src/test/java/com/example/workspace/reservation/controller/ReservationControllerValidationTest.java`
   - Spring Boot test setup with MockMvc or TestRestTemplate
   - All 13 test cases (Groups A–E)
   - Tests should initially fail (no endpoints or validation implemented yet)

---

## Proposed Test Code Snippets

### ReservationControllerValidationTest.java Structure

```java
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
```

---

## Verification Command and Expected RED Evidence

**Verification Command**:
```bash
./mvnw test -Dtest=ReservationControllerValidationTest
```

**Expected Outcome (RED Phase)**:
- **13 tests defined** in `ReservationControllerValidationTest`
- **All tests fail or are skipped** initially (no HTTP endpoints or validation logic implemented yet)
- Maven compilation succeeds (test code is valid Java)
- Test runner output shows test count, failure count, and clear failure messages indicating:
  - 404 for endpoints not found (endpoints don't exist yet)
  - HTTP 404 or 405 Method Not Allowed (Spring routing not configured)
  - Request validation not performed (validation layer not yet implemented)
- No production code for controllers, validators, or error handlers
- No DTO classes in production code

**Example Expected Output** (excerpt):
```
[INFO] --- maven-surefire-plugin:... ---
[INFO] Running com.example.workspace.reservation.controller.ReservationControllerValidationTest

[ERROR] FAILURE: testCreateReservationMissingWorkspaceIdReturns400
[ERROR]  java.io.IOException: Server returned HTTP response code: 404
[ERROR] FAILURE: testGetReservationNonExistentIdReturns404
[ERROR]  java.io.IOException: Server returned HTTP response code: 404
[ERROR] FAILURE: testCancelReservationNonExistentIdReturns404
[ERROR]  java.io.IOException: Server returned HTTP response code: 404
...
[ERROR] Tests run: 13, Failures: 13, Skipped: 0

[INFO] BUILD FAILURE
```

**Evidence Captured**:
- Test file path and class name: `src/test/java/com/example/workspace/reservation/controller/ReservationControllerValidationTest.java`
- Test method names and @DisplayName annotations
- Maven output showing test execution and failure count
- No production endpoint controllers, validators, or error handlers

---

## Test Execution Notes

### Running Validation Tests Only

**Full validation test suite**:
```bash
./mvnw test -Dtest=ReservationControllerValidationTest
```

**Single test**:
```bash
./mvnw test -Dtest=ReservationControllerValidationTest#testCreateReservationMissingWorkspaceIdReturns400
```

### Test Framework Details

- **Framework**: JUnit 5 (from spring-boot-starter-test)
- **Spring Test**: @SpringBootTest with MockMvc
- **HTTP Client**: MockMvc via @AutoConfigureMockMvc
- **JSON Assertion**: jsonPath() for MockMvc, ObjectMapper for manual parsing
- **JSON Parsing**: Jackson ObjectMapper (included in Spring Boot)

### Test Structure Pattern

Each test follows:
1. Arrange: Prepare request body (missing fields, invalid times, invalid UUIDs)
2. Act: Perform HTTP request (POST, GET, DELETE)
3. Assert: Verify HTTP status code (400, 404)
4. Assert: Verify error response JSON structure (`error`, `details` fields)
5. Assert: Verify Content-Type is application/json

---

## Exclusions - What This Phase Does NOT Implement

**Explicitly Excluded** (per Milestone 3 scope):

1. **HTTP Endpoint Controllers**
   - No Spring @RestController
   - No @PostMapping, @GetMapping, @DeleteMapping
   - No endpoint method implementations
   - (This is Milestone 8 - GREEN)

2. **Request/Response DTOs**
   - No CreateReservationRequest DTO
   - No ReservationResponse DTO
   - No error response DTO classes (structure tested, not classes)
   - (These are production code in Milestone 4 - GREEN)

3. **Validation Logic Implementation**
   - No validator class or service
   - No @Validated annotation usage
   - No validation exception handling
   - (This is Milestone 4 - GREEN)

4. **Conflict Detection**
   - No overlap detection logic
   - No concurrency-safe conflict prevention at HTTP layer
   - (This is Milestone 5–6)

5. **Success Path Testing**
   - No POST 201 Created response tests
   - No GET 200 OK response tests
   - No DELETE 204 No Content response tests
   - (These are Milestone 7 - RED, Milestone 8 - GREEN)

6. **Concurrency at HTTP Layer**
   - No concurrent request tests
   - (These are Milestone 7 - RED)

---

## Success and Failure Criteria

### RED Phase Success Criteria

✓ **Success** means:
1. All 13 tests compile and are runnable via `./mvnw test -Dtest=ReservationControllerValidationTest`
2. All 13 tests **fail** initially (endpoints don't exist, no validation logic)
3. Test output clearly documents expected error responses:
   - 400 for validation errors
   - 404 for resource not found
   - Error response JSON structure with `error` and `details` fields
4. No production endpoint code exists
5. No production validation logic exists
6. No DTO classes in production code
7. Tests use Spring MockMvc for HTTP transport verification
8. Tests verify both HTTP status codes and response body structure
9. All test code follows naming conventions and includes @DisplayName annotations

✗ **Failure** means:
- Tests pass before production implementation (cart before horse)
- Tests only verify domain logic, not HTTP transport
- Tests missing error response structure verification
- pom.xml has unnecessary production dependencies
- Endpoint controllers or validation logic already exists
- Tests fail to compile
- Tests don't use Spring Boot test framework (MockMvc, TestRestTemplate)

---

## Repository Artifacts Summary

| File | Type | Status | Purpose |
|------|------|--------|---------|
| src/test/java/com/example/workspace/reservation/controller/ReservationControllerValidationTest.java | Test | To Create | HTTP validation and error handling tests (13 test cases) |

---

## Next Phase

Upon completion and human review of this RED phase (all tests fail, no GREEN implementation):

- **Next Milestone**: Milestone 4 (Validation and Error Handling - GREEN)
- **Goal**: Implement request validation, error response handlers, and HTTP endpoints to pass all RED tests
- **Deliverable**: Production code (validator, error handler, DTO classes) making all 13 tests pass
- **Authorization**: Human review required before proceeding to GREEN

---

## Approval Status

**Status**: Pending human review  
**Not Approved**: This Implementation Plan is proposed for review. Human authorization is required before executing the RED phase (test creation).

**Reviewer Checklist**:
- [ ] Plan aligns with approved Milestone 3 scope (docs/.ai/Plan.md)
- [ ] Test cases cover required validation errors (required fields, time range)
- [ ] Test cases cover required HTTP error responses (400, 404)
- [ ] Error response JSON structure tests verify `error` and `details` fields
- [ ] Tests use Spring Boot test framework (MockMvc or TestRestTemplate)
- [ ] Tests are integration-level (verify HTTP transport, not just domain logic)
- [ ] Tests correctly target `/api/v1/reservations` endpoints
- [ ] GET and DELETE tests verify 404 for non-existent reservation
- [ ] POST tests verify 400 for validation failures
- [ ] All 13 test cases are clearly documented with expected behavior
- [ ] Tests exclude success paths (201, 200, 204) - those are Milestone 7-8
- [ ] Tests exclude conflict detection (409) - that is Milestone 5-6
- [ ] No production code is created (tests only)
- [ ] Exclusions are explicitly documented
- [ ] Plan is ready for implementation without further clarification


