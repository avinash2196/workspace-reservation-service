# Implementation Plan: Milestone 4 - Validation and Error Handling - GREEN

**Milestone**: 4  
**Phase**: GREEN  
**Title**: Validation and Error Handling - GREEN  
**Status**: Ready for Human Review  
**Not Approved**: This plan requires human review before authorization to proceed with production implementation.

---

## Traceability

**Approved Baseline**:
- docs/requirements.md § Create Reservation (Validation Rules), § Get Reservation, § Cancel Reservation
- docs/.ai/Plan.md § Milestone 4: Validation and Error Handling - GREEN
- docs/.ai/0_API_Contract.md § Error Responses, Validation
- docs/.ai/003_Implementation_Plan_Validation_Error_Handling_RED.md § RED test contract (13 tests)

**This Plan Addresses**: Production implementation of request validation, error response handling, and HTTP request/response DTO mapping to make all Milestone 3 (RED) tests pass.

---

## Current Repository State

**Completed (Milestones 1–3)**:
- Domain layer: `Reservation.java`, `ReservationStatus.java` (immutable, fully tested)
- Store layer: `ReservationStore.java` with thread-safe ConcurrentHashMap (fully tested, 20 passing tests)
- Test layer: `ReservationTest.java`, `ReservationStoreTest.java`, `ReservationControllerValidationTest.java` (all 33 tests defined)
  - Milestone 1-2 tests: 20 passing ✓
  - Milestone 3 RED tests: 13 failing (expected - no HTTP endpoints or validation logic yet)
- Build: `pom.xml` configured for Maven, Java 17, Spring Boot 3, JUnit 5

**Incomplete (Blocking Milestone 4 GREEN)**:
- HTTP controller class (`ReservationController.java` - does not exist)
- Request/response DTOs (do not exist)
- Validation service or validator classes (do not exist)
- Global exception handler for error responses (does not exist)
- Custom validation exception classes (do not exist)

**Missing Files to Create**:
1. `src/main/java/com/example/workspace/reservation/dto/CreateReservationRequest.java` - Request DTO
2. `src/main/java/com/example/workspace/reservation/dto/ReservationResponse.java` - Response DTO
3. `src/main/java/com/example/workspace/reservation/dto/ErrorResponse.java` - Error response DTO
4. `src/main/java/com/example/workspace/reservation/exception/ValidationException.java` - Custom exception
5. `src/main/java/com/example/workspace/reservation/service/ReservationValidator.java` - Validation service
6. `src/main/java/com/example/workspace/reservation/controller/ReservationController.java` - HTTP controller
7. `src/main/java/com/example/workspace/reservation/controller/GlobalExceptionHandler.java` - Exception handler

---

## Milestone 4 Scope: Validation and Error Handling - GREEN

**Goal**: Implement request validation and error response handling to satisfy all Milestone 3 (RED) test contract requirements.

**Success Criteria**:
- All 13 Milestone 3 (RED) tests pass
- Validation logic is testable independently of HTTP layer (validation service has clear contract)
- Error responses conform to API Contract (error + details JSON structure)
- HTTP status codes match contract (400, 404)
- Validation exceptions map correctly to HTTP error responses
- No conflict detection logic (that is Milestone 5–6)
- No full HTTP endpoint implementation beyond what's required for validation tests

**Test Coverage by Implementation**:

| Test Group | Tests | HTTP Status | Implementation Focus |
|------------|-------|------------|----------------------|
| A: Required Field Validation | 5 | 400 | Missing workspaceId, userId, startTime, endTime |
| B: Time Range Validation | 2 | 400 | startTime must be strictly before endTime |
| C: Error Response Structure | 1 | 400 | JSON structure with error + details fields |
| D: GET Not Found | 2 | 404 / 400 | Non-existent id, invalid UUID format |
| E: DELETE Not Found | 2 | 404 / 400 | Non-existent id, invalid UUID format |
| **Total** | **13** | — | — |

**Exclusions**:
- No conflict detection (Milestone 5–6)
- No success path endpoints (201 Created, 200 OK, 204 No Content - those are Milestone 7–8)
- No POST endpoint implementation for creating reservations (conflict detection is prerequisite)
- No concurrency tests at HTTP layer (those are Milestone 7)
- No unrelated refactoring or optimization

---

## Architectural Approach

**Design Principle**: Validation logic is independent of HTTP transport. Validation service can be called directly by tests or by controller.

**Layer Responsibilities**:
1. **DTOs** (`dto/` package): HTTP request/response transport models (no business logic)
2. **Validation Service** (`service/ReservationValidator`): Stateless validation rules and error messages
3. **Exceptions** (`exception/` package): Custom exceptions for validation failures
4. **Controller** (`controller/ReservationController`): HTTP endpoint routing and request/response mapping
5. **Exception Handler** (`controller/GlobalExceptionHandler`): Maps validation exceptions to HTTP error responses

**Validation Flow**:
```
HTTP Request
    ↓
Controller deserializes to DTO
    ↓
Validator checks DTO fields
    ↓
ValidationException thrown if invalid
    ↓
GlobalExceptionHandler catches exception
    ↓
ErrorResponse JSON returned with HTTP status code
```

---

## Files to Create / Modify

### 1. `src/main/java/com/example/workspace/reservation/dto/CreateReservationRequest.java`

**Status**: To Create  
**Priority**: Critical (GREEN phase production code)  
**Content**: Request DTO for POST /api/v1/reservations

**Purpose**: Transport model for incoming JSON request body. No business logic, only data holders and Jackson serialization support.

**Design**:
- Package: `dto` (separate from domain)
- Class: `CreateReservationRequest` (records or POJO)
- Fields: `workspaceId`, `userId`, `startTime`, `endTime` (all String for JSON transport)
- No validation logic in DTO itself (validation is service layer responsibility)
- Supports Jackson deserialization with default constructor

**Proposed Code**:
```java
package com.example.workspace.reservation.dto;

public class CreateReservationRequest {
    private String workspaceId;
    private String userId;
    private String startTime;
    private String endTime;

    public CreateReservationRequest() {
    }

    public CreateReservationRequest(String workspaceId, String userId, String startTime, String endTime) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
```

---

### 2. `src/main/java/com/example/workspace/reservation/dto/ReservationResponse.java`

**Status**: To Create  
**Priority**: Critical (GREEN phase production code)  
**Content**: Response DTO for successful GET and POST responses (201, 200 OK)

**Note**: This is created for completeness and future use (Milestone 7-8), but Milestone 4 tests focus on error paths (400, 404), not success paths (201, 200).

**Design**:
- Package: `dto`
- Class: `ReservationResponse`
- Fields: `id`, `workspaceId`, `userId`, `startTime`, `endTime`, `status` (all Strings or enums for JSON)
- Converts from domain `Reservation` object to JSON-serializable form

**Proposed Code**:
```java
package com.example.workspace.reservation.dto;

import com.example.workspace.reservation.domain.ReservationStatus;

public class ReservationResponse {
    private String id;
    private String workspaceId;
    private String userId;
    private String startTime;
    private String endTime;
    private String status;

    public ReservationResponse(String id, String workspaceId, String userId,
                             String startTime, String endTime, String status) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getUserId() {
        return userId;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getStatus() {
        return status;
    }
}
```

---

### 3. `src/main/java/com/example/workspace/reservation/dto/ErrorResponse.java`

**Status**: To Create  
**Priority**: Critical (GREEN phase production code)  
**Content**: Error response DTO matching API Contract error structure

**Design**:
- Package: `dto`
- Class: `ErrorResponse`
- Fields: `error` (String - error classification), `details` (String - descriptive message)
- Matches API Contract schema (0_API_Contract.md § Error Response Object)

**Proposed Code**:
```java
package com.example.workspace.reservation.dto;

public class ErrorResponse {
    private String error;
    private String details;

    public ErrorResponse(String error, String details) {
        this.error = error;
        this.details = details;
    }

    public String getError() {
        return error;
    }

    public String getDetails() {
        return details;
    }
}
```

---

### 4. `src/main/java/com/example/workspace/reservation/exception/ValidationException.java`

**Status**: To Create  
**Priority**: Critical (GREEN phase production code)  
**Content**: Custom exception for validation failures

**Design**:
- Package: `exception`
- Class: `ValidationException` (extends `RuntimeException`)
- Fields: `errorCode` (String), `message` (String)
- Carries validation error information for GlobalExceptionHandler to map to HTTP 400 response

**Proposed Code**:
```java
package com.example.workspace.reservation.exception;

public class ValidationException extends RuntimeException {
    private final String errorCode;

    public ValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
```

---

### 5. `src/main/java/com/example/workspace/reservation/exception/ResourceNotFoundException.java`

**Status**: To Create  
**Priority**: Critical (GREEN phase production code)  
**Content**: Custom exception for 404 Not Found errors

**Design**:
- Package: `exception`
- Class: `ResourceNotFoundException` (extends `RuntimeException`)
- Fields: `resourceId` (String), message (String)
- Carries resource not found information for GlobalExceptionHandler to map to HTTP 404 response

**Proposed Code**:
```java
package com.example.workspace.reservation.exception;

public class ResourceNotFoundException extends RuntimeException {
    private final String resourceId;

    public ResourceNotFoundException(String resourceId, String message) {
        super(message);
        this.resourceId = resourceId;
    }

    public String getResourceId() {
        return resourceId;
    }
}
```

---

### 6. `src/main/java/com/example/workspace/reservation/service/ReservationValidator.java`

**Status**: To Create  
**Priority**: Critical (GREEN phase production code)  
**Content**: Stateless validation service for reservation creation requests

**Design**:
- Package: `service`
- Class: `ReservationValidator` (stateless utility class or Spring @Component)
- Methods:
  - `validateCreateReservationRequest(CreateReservationRequest)`: Validates all fields and time range
  - `validateUUID(String id)`: Validates UUID format for path parameters
- Throws `ValidationException` with descriptive error messages
- Validation Rules:
  - All fields (workspaceId, userId, startTime, endTime) are required (non-null, non-empty)
  - startTime and endTime must be parseable as ISO 8601 UTC datetime
  - startTime must be strictly before endTime (not equal, not after)

**Proposed Code**:
```java
package com.example.workspace.reservation.service;

import com.example.workspace.reservation.dto.CreateReservationRequest;
import com.example.workspace.reservation.exception.ValidationException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationValidator {

    public void validateCreateReservationRequest(CreateReservationRequest request) {
        if (request.getWorkspaceId() == null || request.getWorkspaceId().isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "Field 'workspaceId' is required");
        }

        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "Field 'userId' is required");
        }

        if (request.getStartTime() == null || request.getStartTime().isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "Field 'startTime' is required");
        }

        if (request.getEndTime() == null || request.getEndTime().isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "Field 'endTime' is required");
        }

        LocalDateTime startTime = parseDateTime(request.getStartTime(), "startTime");
        LocalDateTime endTime = parseDateTime(request.getEndTime(), "endTime");

        if (!startTime.isBefore(endTime)) {
            throw new ValidationException("VALIDATION_ERROR",
                    "startTime must be strictly before endTime");
        }
    }

    public void validateUUID(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("VALIDATION_ERROR",
                    "Invalid UUID format: " + id);
        }
    }

    private LocalDateTime parseDateTime(String dateTimeString, String fieldName) {
        try {
            return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new ValidationException("VALIDATION_ERROR",
                    "Field '" + fieldName + "' must be a valid ISO 8601 datetime in UTC format");
        }
    }
}
```

---

### 7. `src/main/java/com/example/workspace/reservation/controller/ReservationController.java`

**Status**: To Create  
**Priority**: Critical (GREEN phase production code)  
**Content**: HTTP Spring Boot REST controller with endpoints for reservation operations

**Design**:
- Package: `controller`
- Class: `ReservationController` (Spring @RestController)
- Endpoints:
  - `POST /api/v1/reservations` - Request validation only (no creation logic for conflict detection - that's Milestone 5-6)
  - `GET /api/v1/reservations/{id}` - Path parameter validation and retrieval from store
  - `DELETE /api/v1/reservations/{id}` - Path parameter validation and cancellation from store
- Responsibilities:
  - HTTP request/response transport
  - DTO deserialization
  - Call validator for validation errors
  - Call store for data access
  - Throw exceptions caught by GlobalExceptionHandler
- Notes:
  - For POST /api/v1/reservations: Tests only verify validation errors (400) at this stage; no conflict detection yet
  - For GET and DELETE: Verify 404 for missing and 400 for invalid UUIDs

**Proposed Code**:
```java
package com.example.workspace.reservation.controller;

import com.example.workspace.reservation.dto.CreateReservationRequest;
import com.example.workspace.reservation.dto.ReservationResponse;
import com.example.workspace.reservation.exception.ResourceNotFoundException;
import com.example.workspace.reservation.service.ReservationValidator;
import com.example.workspace.reservation.store.ReservationStore;
import com.example.workspace.reservation.domain.Reservation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationValidator validator;
    private final ReservationStore store;

    @Autowired
    public ReservationController(ReservationValidator validator, ReservationStore store) {
        this.validator = validator;
        this.store = store;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@RequestBody CreateReservationRequest request) {
        // Validate request
        validator.validateCreateReservationRequest(request);

        // Parse datetime fields
        LocalDateTime startTime = LocalDateTime.parse(request.getStartTime(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        LocalDateTime endTime = LocalDateTime.parse(request.getEndTime(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // Create reservation in domain model (conflict detection is Milestone 5-6)
        Reservation reservation = Reservation.create(
                request.getWorkspaceId(),
                request.getUserId(),
                startTime,
                endTime
        );

        // Store reservation
        Reservation storedReservation = store.create(reservation);

        // Return 201 Created response (note: actual conflict detection happens in Milestone 6)
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(storedReservation));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable String id) {
        // Validate UUID format
        validator.validateUUID(id);

        UUID uuid = UUID.fromString(id);

        // Retrieve from store
        Reservation reservation = store.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException(id,
                        "Reservation with id '" + id + "' not found"));

        return ResponseEntity.ok(toResponse(reservation));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelReservation(@PathVariable String id) {
        // Validate UUID format
        validator.validateUUID(id);

        UUID uuid = UUID.fromString(id);

        // Check reservation exists before cancelling
        if (store.findById(uuid).isEmpty()) {
            throw new ResourceNotFoundException(id,
                    "Reservation with id '" + id + "' not found");
        }

        // Cancel reservation
        store.cancel(uuid);

        return ResponseEntity.noContent().build();
    }

    private ReservationResponse toResponse(Reservation reservation) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        return new ReservationResponse(
                reservation.getId().toString(),
                reservation.getWorkspaceId(),
                reservation.getUserId(),
                reservation.getStartTime().format(formatter),
                reservation.getEndTime().format(formatter),
                reservation.getStatus().toString()
        );
    }
}
```

---

### 8. `src/main/java/com/example/workspace/reservation/controller/GlobalExceptionHandler.java`

**Status**: To Create  
**Priority**: Critical (GREEN phase production code)  
**Content**: Global exception handler for validation and not-found exceptions, maps to HTTP error responses

**Design**:
- Package: `controller`
- Class: `GlobalExceptionHandler` (Spring @ControllerAdvice)
- Methods:
  - `handleValidationException(ValidationException)`: Returns 400 Bad Request with error response
  - `handleResourceNotFound(ResourceNotFoundException)`: Returns 404 Not Found with error response
- Maps exceptions to ErrorResponse DTO with correct JSON structure

**Proposed Code**:
```java
package com.example.workspace.reservation.controller;

import com.example.workspace.reservation.dto.ErrorResponse;
import com.example.workspace.reservation.exception.ValidationException;
import com.example.workspace.reservation.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getErrorCode(),
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                "NOT_FOUND",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
}
```

---

## Ordered Implementation Changes

**Phase**: GREEN (Production Code)

1. **Step 1**: Create DTO classes (transport layer)
   - `src/main/java/com/example/workspace/reservation/dto/CreateReservationRequest.java`
   - `src/main/java/com/example/workspace/reservation/dto/ReservationResponse.java`
   - `src/main/java/com/example/workspace/reservation/dto/ErrorResponse.java`

2. **Step 2**: Create exception classes (exception layer)
   - `src/main/java/com/example/workspace/reservation/exception/ValidationException.java`
   - `src/main/java/com/example/workspace/reservation/exception/ResourceNotFoundException.java`

3. **Step 3**: Create validation service (business logic layer)
   - `src/main/java/com/example/workspace/reservation/service/ReservationValidator.java`

4. **Step 4**: Create exception handler (HTTP layer)
   - `src/main/java/com/example/workspace/reservation/controller/GlobalExceptionHandler.java`

5. **Step 5**: Create HTTP controller (HTTP layer)
   - `src/main/java/com/example/workspace/reservation/controller/ReservationController.java`

**Execution Duration**: Five new files, ~400 lines of production code total

---

## Test Coverage Mapping

| Milestone 3 Test | HTTP Status | Implementation Component | Validation |
|------------------|-------------|--------------------------|------------|
| testCreateReservationMissingWorkspaceIdReturns400 | 400 | ReservationValidator + GlobalExceptionHandler | Field presence |
| testCreateReservationMissingUserIdReturns400 | 400 | ReservationValidator + GlobalExceptionHandler | Field presence |
| testCreateReservationMissingStartTimeReturns400 | 400 | ReservationValidator + GlobalExceptionHandler | Field presence |
| testCreateReservationMissingEndTimeReturns400 | 400 | ReservationValidator + GlobalExceptionHandler | Field presence |
| testCreateReservationMissingMultipleFieldsReturns400 | 400 | ReservationValidator + GlobalExceptionHandler | Multiple fields |
| testCreateReservationEmptyRequestBodyReturns400 | 400 | ReservationValidator + GlobalExceptionHandler | All fields missing |
| testCreateReservationStartTimeEqualsEndTimeReturns400 | 400 | ReservationValidator + GlobalExceptionHandler | Time ordering |
| testCreateReservationStartTimeAfterEndTimeReturns400 | 400 | ReservationValidator + GlobalExceptionHandler | Time ordering |
| testCreateReservationValidationErrorHasCorrectErrorResponseStructure | 400 | GlobalExceptionHandler → ErrorResponse | Response structure |
| testGetReservationNonExistentIdReturns404 | 404 | ReservationController → ResourceNotFoundException → GlobalExceptionHandler | Store lookup |
| testGetReservationInvalidUuidFormatReturns400 | 400 | ReservationValidator + GlobalExceptionHandler | UUID format |
| testCancelReservationNonExistentIdReturns404 | 404 | ReservationController → ResourceNotFoundException → GlobalExceptionHandler | Store lookup |
| testCancelReservationInvalidUuidFormatReturns400 | 400 | ReservationValidator + GlobalExceptionHandler | UUID format |

---

## Verification

### Unit Test Execution
Run validation tests to verify GREEN criteria:
```bash
./mvnw test -Dtest=ReservationControllerValidationTest
```

**Expected Result**:
- All 13 Milestone 3 (RED) tests pass
- All 20 Milestone 1-2 tests continue to pass
- Total: 33 passing tests

**Success Criteria**:
```
Tests run: 33, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS
```

### Full Test Suite
```bash
./mvnw test
```

**Expected Result**: All tests pass (Milestone 1, 2, 3 combined)

---

## Scope Boundary

### In Scope (Milestone 4 - GREEN)
✓ Request/Response DTOs (transport models, no business logic)  
✓ Validation of required fields (workspaceId, userId, startTime, endTime)  
✓ DateTime parsing and validation (ISO 8601 UTC format)  
✓ Time range validation (startTime strictly before endTime)  
✓ UUID format validation for path parameters  
✓ HTTP 400 Bad Request for validation failures  
✓ HTTP 404 Not Found for missing reservations  
✓ Error response structure (error + details JSON)  
✓ Exception handling via @ControllerAdvice  
✓ HTTP controller endpoints (limited to validation testing scope)  
✓ GET and DELETE endpoint implementation (store lookup + error handling)  

### Explicitly Out of Scope (Deferred to Future Milestones)
✗ Conflict detection / overlap logic (Milestone 5–6)  
✗ Concurrency tests at HTTP layer (Milestone 7)  
✗ POST endpoint creation success path (201 Created is implemented but not tested in Milestone 3)  
✗ Unrelated refactoring or optimization  
✗ Logging or observability beyond Spring Boot defaults  
✗ Performance tuning  

### Repository Exclusions (Per Approved Requirements)
✗ Production database or persistence layer  
✗ JPA, ORM, or data access frameworks  
✗ Flyway or database migrations  
✗ Message queues or event publishing  
✗ Caching layer  
✗ External service integrations  
✗ Authentication or authorization  

---

## Risk Assessment

**Risk Level**: Low

**Rationale**:
- All exceptions are standard Spring @ControllerAdvice patterns
- DTOs use standard Jackson serialization (included in spring-boot-starter-web)
- Validation service is stateless and testable independently
- No dependency on future milestones (Milestone 5-6) for these features
- All error paths are tested by Milestone 3 RED tests

**Mitigations**:
- Run full test suite after implementation (`./mvnw test`)
- Validation tests explicitly verify error response structure and status codes
- Test failures will immediately indicate incorrect mapping or missing validation

---

## Implementation Notes

### DateTime Parsing Strategy

**Choice**: Use `LocalDateTime` with `DateTimeFormatter.ISO_OFFSET_DATE_TIME`

**Rationale**:
- API Contract specifies "ISO 8601 UTC datetime" (e.g., "2025-09-07T14:30:00Z")
- Jackson deserializes ISO strings to LocalDateTime by default
- LocalDateTime comparison is exact (accounts for precision per requirements)
- No Timezone handling complexity (all times are UTC)

### Validation Service Independence

**Design**: `ReservationValidator` is stateless and can be called independently
- No dependency on Spring context or HTTP request
- Can be unit tested directly without MockMvc
- Controller calls validator before store operations
- Future milestone can add service-layer validation tests

### Error Response Structure

**Contract Compliance**:
- All error responses include exactly two fields: `error` and `details`
- `error`: Classification (e.g., "VALIDATION_ERROR", "NOT_FOUND", "CONFLICT")
- `details`: Descriptive message for client debugging
- Matches 0_API_Contract.md § Error Response Object

### No Conflict Detection

**Why POST still works without conflict detection**:
- Milestone 3 RED tests only verify validation errors (400, 404)
- POST endpoint accepts and stores reservations without checking overlaps
- Conflict detection (409 Conflict) is Milestone 5–6 responsibility
- Tests will not verify overlap behavior in Milestone 4

**Impact**:
- Tests can safely create overlapping reservations without errors
- Tests exercise validation and error paths, not overlap logic
- Conflict detection added later without changing validation layer

---

## No Unrelated Refactoring

This implementation introduces only Milestone 4 scope:
- No reorganization of existing domain or store classes
- No changes to Reservation.java or ReservationStore.java
- No changes to test code
- No updates to build configuration or dependencies
- No changes to repository structure beyond adding `dto/`, `exception/`, and `service/` packages

---

## Completion Criteria for Milestone 4 (GREEN)

✓ All 13 Milestone 3 (RED) tests pass  
✓ All 20 Milestone 1-2 tests continue to pass  
✓ Request validation catches all required field failures  
✓ DateTime parsing and validation works correctly  
✓ UUID format validation for path parameters works  
✓ Error responses have correct JSON structure (error + details)  
✓ HTTP status codes match contract (400, 404)  
✓ Validation logic is independent of HTTP layer  
✓ No conflict detection logic introduced (deferred to Milestone 5)  
✓ No unrelated changes or refactoring  

Once all tests pass and code review approves implementation, Milestone 4 (GREEN) is complete and ready for human review before proceeding to Milestone 5 (RED).

---

## Next Phase

Upon completion and human review of this GREEN phase (all 13 validation tests pass):

- **Next Milestone**: Milestone 5 (Conflict Detection and Concurrency - RED)
- **Goal**: Define test contract for overlap detection and concurrency-safe conflict prevention
- **Deliverable**: Test code verifying overlap logic, non-overlapping adjacent times, and concurrency guarantees
- **Authorization**: Human review required before proceeding to RED

---

## Approval Status

**Status**: Pending human review  
**Not Approved**: This Implementation Plan is proposed for review. Human authorization is required before executing the GREEN phase (production implementation).

**Reviewer Checklist**:
- [ ] Plan aligns with approved Milestone 4 scope (docs/.ai/Plan.md)
- [ ] Proposed DTOs match API Contract request/response schemas
- [ ] Validation service covers all required field validations
- [ ] Time range validation correctly enforces startTime < endTime
- [ ] Error response structure matches contract (error + details)
- [ ] HTTP status codes are correct (400 for validation, 404 for missing)
- [ ] Exception handlers correctly map exceptions to HTTP responses
- [ ] Controller endpoints handle validation and error cases
- [ ] UUID format validation included for path parameters
- [ ] No conflict detection logic included (that's Milestone 5-6)
- [ ] No success path implementation beyond what's needed for tests
- [ ] All 13 Milestone 3 tests will pass with these implementations
- [ ] No unrelated refactoring or scope creep
- [ ] All new files are in appropriate packages (dto/, exception/, service/, controller/)
- [ ] Plan is ready for implementation without further clarification


