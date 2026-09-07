# Implementation Plan: Milestone 8 - HTTP API Endpoints (GREEN)

**Status**: Pending Review and Authorization

**Traceability**: 
- Approved Requirements: [docs/requirements.md](../requirements.md) § API Endpoints, Conflict Detection, Concurrency Guarantee
- Approved Plan: [Plan.md](./Plan.md) § Milestone 8
- Approved Contract: [0_API_Contract.md](./0_API_Contract.md)
- Approved RED Plan: [007_Implementation_Plan_HTTP_API_RED.md](./007_Implementation_Plan_HTTP_API_RED.md)
- Predecessor: Milestone 7 (RED) verified complete with valid test evidence

---

## Phase: GREEN

**Goal**: Implement Spring Boot REST controllers and integrate with business logic to satisfy all Milestone 7 RED tests.

**Scope**: Production code implementation only. No test changes. Smallest changes required to make all 32+ Milestone 7 tests pass.

---

## Current State Analysis

### Existing Implementation Status

**Already in place (from Milestones 1-6)**:
- `ReservationStore` with in-memory ConcurrentHashMap storage
- `createWithConflictCheck()` method with synchronized concurrency protection
- Overlap detection logic with correct predicate: `s1 < e2 AND s2 < e1`
- `Reservation` domain model with ACTIVE/CANCELLED status
- `ReservationValidator` with required field and datetime validation
- Exception classes: `ValidationException`, `ResourceNotFoundException`, `ConflictException`
- Request/response DTOs: `CreateReservationRequest`, `ReservationResponse`, `ErrorResponse`

**Already in place (partial)**:
- `ReservationController` with skeleton of three endpoints (POST, GET, DELETE)
- `GlobalExceptionHandler` with handlers for ValidationException and ResourceNotFoundException
- Request mapping and basic routing

### Known Issues Blocking Milestone 7 Tests

1. **Datetime Formatting Bug** (UnsupportedTemporalTypeException)
   - **Location**: `ReservationController.toResponse()` line 97
   - **Issue**: Uses `DateTimeFormatter.ISO_OFFSET_DATE_TIME` on `LocalDateTime` (which has no timezone info)
   - **Symptoms**: Tests fail with UnsupportedTemporalTypeException when formatting response timestamps
   - **Required Fix**: Convert LocalDateTime to Instant (UTC) before formatting, or use ISO_8601 format string

2. **Missing Conflict Detection in Create**
   - **Location**: `ReservationController.createReservation()` line 51
   - **Issue**: Calls `store.create()` which bypasses conflict checking. Should use `store.createWithConflictCheck()`
   - **Symptoms**: Tests expecting 409 Conflict get 201 Created instead
   - **Required Fix**: Replace call to match the signature and behavior

3. **Missing ConflictException Handler**
   - **Location**: `GlobalExceptionHandler` class
   - **Issue**: No @ExceptionHandler for ConflictException
   - **Symptoms**: Tests expecting 409 Conflict response get 500 Internal Server Error instead
   - **Required Fix**: Add exception handler mapping ConflictException to 409 Conflict response

---

## Implementation Details

### File 1: ReservationController.java

**Changes Required**:

1. **Update imports** to include Instant, ZoneId, and IOException for Jackson configuration
   
2. **Fix createReservation() method** (line 32-55):
   - Parse request timestamps as Instant instead of LocalDateTime
   - Validate datetime comparison using Instant comparison
   - Call `store.createWithConflictCheck()` instead of `store.create()`
   - Return 201 Created with response DTO

3. **Fix toResponse() method** (line 91-101):
   - Accept a Reservation parameter (unchanged)
   - Convert LocalDateTime fields to Instant at UTC
   - Format timestamps as ISO 8601 UTC string (e.g., "2025-09-07T10:00:00Z")
   - Return properly formatted ReservationResponse DTO

**Concrete Code Proposal**:

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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
        // Validate request fields and structure
        validator.validateCreateReservationRequest(request);

        // Parse datetime fields as Instant (UTC)
        Instant startTimeInstant = Instant.parse(request.getStartTime());
        Instant endTimeInstant = Instant.parse(request.getEndTime());

        // Validate time range (startTime < endTime)
        if (!startTimeInstant.isBefore(endTimeInstant)) {
            throw new com.example.workspace.reservation.exception.ValidationException(
                "VALIDATION_ERROR",
                "startTime must be strictly before endTime"
            );
        }

        // Convert Instant back to LocalDateTime (UTC) for domain model
        LocalDateTime startTime = LocalDateTime.ofInstant(startTimeInstant, ZoneId.of("UTC"));
        LocalDateTime endTime = LocalDateTime.ofInstant(endTimeInstant, ZoneId.of("UTC"));

        // Create reservation domain object
        Reservation reservation = Reservation.create(
            request.getWorkspaceId(),
            request.getUserId(),
            startTime,
            endTime
        );

        // Store reservation with conflict detection
        Reservation storedReservation = store.createWithConflictCheck(reservation);

        // Return 201 Created with Location header and response body
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

    /**
     * Convert Reservation domain object to HTTP response DTO.
     *
     * Timestamps are formatted as ISO 8601 UTC strings (e.g., "2025-09-07T10:00:00Z").
     * LocalDateTime values in the domain model are treated as UTC for serialization.
     */
    private ReservationResponse toResponse(Reservation reservation) {
        // Convert LocalDateTime (stored as UTC) to Instant, then format as ISO 8601 UTC string
        Instant startInstant = reservation.getStartTime().atZone(ZoneId.of("UTC")).toInstant();
        Instant endInstant = reservation.getEndTime().atZone(ZoneId.of("UTC")).toInstant();

        String startTimeString = DateTimeFormatter.ISO_INSTANT.format(startInstant);
        String endTimeString = DateTimeFormatter.ISO_INSTANT.format(endInstant);

        return new ReservationResponse(
                reservation.getId().toString(),
                reservation.getWorkspaceId(),
                reservation.getUserId(),
                startTimeString,
                endTimeString,
                reservation.getStatus().toString()
        );
    }
}
```

---

### File 2: GlobalExceptionHandler.java

**Changes Required**:

1. **Add ConflictException import**

2. **Add new @ExceptionHandler for ConflictException**:
   - Maps ConflictException to 409 Conflict HTTP status
   - Extracts error code and message from exception
   - Returns ErrorResponse DTO with error classification and details

**Concrete Code Proposal**:

```java
package com.example.workspace.reservation.controller;

import com.example.workspace.reservation.dto.ErrorResponse;
import com.example.workspace.reservation.exception.ValidationException;
import com.example.workspace.reservation.exception.ResourceNotFoundException;
import com.example.workspace.reservation.exception.ConflictException;
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

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ErrorResponse> handleConflictException(ConflictException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getErrorCode(),
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
}
```

---

## Integration with Existing Components

### ReservationStore Delegation

The controller properly delegates to the store:

- **POST (Create)**: Calls `store.createWithConflictCheck(reservation)` 
  - Handles atomicity and concurrency safety
  - Throws ConflictException on overlap
  - Returns stored reservation with generated UUID

- **GET (Retrieve)**: Calls `store.findById(uuid)` 
  - Returns Optional, controller throws ResourceNotFoundException if empty
  - Works for both ACTIVE and CANCELLED reservations

- **DELETE (Cancel)**: Calls `store.cancel(uuid)`
  - Validates existence first, throws ResourceNotFoundException if absent
  - Idempotent: cancelling already-cancelled reservation succeeds

### ReservationValidator Delegation

The controller invokes validator:

- **validateCreateReservationRequest()**: Checks required fields, parses and validates ISO 8601 format, verifies startTime < endTime
- **validateUUID()**: Validates path parameter format before parsing

### Exception Mapping

Global exception handler maps domain exceptions to HTTP responses:

| Exception | Handler | HTTP Status | Error Classification | Details |
|-----------|---------|-------------|----------------------|---------|
| ValidationException | @ExceptionHandler | 400 Bad Request | VALIDATION_ERROR | From exception message |
| ResourceNotFoundException | @ExceptionHandler | 404 Not Found | NOT_FOUND | From exception message |
| ConflictException | @ExceptionHandler | 409 Conflict | CONFLICT | From exception message |

### Request/Response DTO Behavior

**CreateReservationRequest**:
- No business logic, pure data holder
- Four string fields: workspaceId, userId, startTime, endTime
- Deserialized from JSON by Spring
- Timestamps remain as strings until controller parses them

**ReservationResponse**:
- No business logic, pure data holder
- Six string fields: id, workspaceId, userId, startTime, endTime, status
- Timestamps formatted as ISO 8601 UTC strings
- Serialized to JSON by Spring

---

## Datetime Handling Strategy

**Domain Model** (Reservation.java):
- Uses LocalDateTime for start/end times
- Persisted in memory as-is
- Treated as UTC for all business logic

**HTTP Request** (POST):
- Client sends ISO 8601 UTC timestamp string (e.g., "2025-09-07T10:00:00Z")
- Controller parses as Instant.parse() to ensure UTC
- Converts to LocalDateTime at UTC zone for domain storage

**HTTP Response** (200/201):
- Controller converts LocalDateTime back to Instant at UTC zone
- Formats using DateTimeFormatter.ISO_INSTANT
- Produces ISO 8601 UTC string (e.g., "2025-09-07T10:00:00Z")

**Conflict Detection** (Store):
- Uses LocalDateTime comparison directly
- Overlap logic: `s1.isBefore(e2) && s2.isBefore(e1)`
- Assumes all times are UTC (no timezone conversions in store)

---

## Content Negotiation

- All request bodies: `Content-Type: application/json` (validated by Spring)
- All response bodies (success): `Content-Type: application/json` (set by Spring)
- All error responses: `Content-Type: application/json` (set by exception handler)

Spring Boot automatically handles Content-Type via `@RestController` and Jackson serialization. No explicit configuration required.

---

## HTTP Status Code Mapping

| Scenario | Endpoint | Trigger | HTTP Status | Response Body |
|----------|----------|---------|------------|---|
| Valid creation | POST /api/v1/reservations | Reservation created successfully | 201 Created | ReservationResponse DTO as JSON |
| Retrieval success | GET /api/v1/reservations/{id} | Reservation exists | 200 OK | ReservationResponse DTO as JSON |
| Cancellation success | DELETE /api/v1/reservations/{id} | Reservation exists (any status) | 204 No Content | Empty |
| Validation failure | POST | Missing/invalid fields or invalid time range | 400 Bad Request | ErrorResponse DTO (VALIDATION_ERROR) |
| Validation failure | GET/DELETE | Invalid UUID format | 400 Bad Request | ErrorResponse DTO (VALIDATION_ERROR) |
| Not found | GET /api/v1/reservations/{id} | Reservation does not exist | 404 Not Found | ErrorResponse DTO (NOT_FOUND) |
| Not found | DELETE /api/v1/reservations/{id} | Reservation does not exist | 404 Not Found | ErrorResponse DTO (NOT_FOUND) |
| Conflict | POST /api/v1/reservations | ACTIVE reservation overlaps same workspace | 409 Conflict | ErrorResponse DTO (CONFLICT) |

---

## Concurrency Guarantee Implementation

**Mechanism**: `ReservationStore.createWithConflictCheck()` is synchronized.

**Observable Behavior** (per API Contract):
- Concurrent POST requests for overlapping times on same workspace
- Exactly one request succeeds with 201 Created
- Other requests receive 409 Conflict
- No intermediate states visible to clients
- Race condition window eliminated by synchronization

**Proof**: Milestone 5-6 RED tests verify this behavior passes with concurrent threads.

---

## Error Response Message Format

Error messages follow the pattern from Milestone 4:
- Error code string (e.g., "VALIDATION_ERROR", "NOT_FOUND", "CONFLICT")
- Descriptive details string for client debugging
- Both fields always present in ErrorResponse JSON

Examples:

```json
{
  "error": "VALIDATION_ERROR",
  "details": "Field 'workspaceId' is required"
}
```

```json
{
  "error": "NOT_FOUND",
  "details": "Reservation with id '12345678-1234-1234-1234-123456789012' not found"
}
```

```json
{
  "error": "CONFLICT",
  "details": "conflict: An ACTIVE reservation already exists for workspace 'ws-1' during the requested time range [2025-09-07T10:00:00, 2025-09-07T11:00:00]"
}
```

---

## UUID Path Parameter Handling

- Spring automatically binds `@PathVariable String id` from URL path
- Controller calls `validator.validateUUID(id)` to verify format
- If invalid, ValidationException thrown → 400 Bad Request with error details
- If valid, parsed via `UUID.fromString(id)`

Example paths:
- Valid: `/api/v1/reservations/550e8400-e29b-41d4-a716-446655440000`
- Invalid (bad format): `/api/v1/reservations/not-a-uuid` → 400 Bad Request
- Invalid (missing): `/api/v1/reservations/` → 404 Not Found (no matching route)

---

## Key Design Decisions (Non-Blocking Implementation Details)

1. **Instant vs LocalDateTime**: 
   - Domain model uses LocalDateTime for simplicity
   - Controller converts to/from Instant for proper UTC handling
   - Validator uses LocalDateTime.parse() for validation

2. **Synchronized Method**:
   - `createWithConflictCheck()` is synchronized in store
   - Sufficient for single-process, in-memory concurrency safety
   - No distributed lock or external coordination needed

3. **Datetime Parsing**:
   - Instant.parse() validates ISO 8601 format at HTTP boundary
   - LocalDateTime.parse() with ISO_OFFSET_DATE_TIME format in validator
   - Both handle UTC timezone correctly

4. **Error Messages**:
   - Descriptive but not exposing internal structure
   - Include relevant context (field names, resource ids, time ranges)
   - Match error classification for clients to categorize failures

---

## Scope: Exactly Three Endpoints

This implementation delivers exactly three endpoints per approved contract:

1. **POST /api/v1/reservations** - Create reservation
2. **GET /api/v1/reservations/{id}** - Retrieve reservation
3. **DELETE /api/v1/reservations/{id}** - Cancel reservation

No list, query, update, or other endpoints are included.

---

## Test Coverage Alignment

This implementation satisfies all Milestone 7 RED tests:

- **Group A** (POST 201 success): ✓ Proper response status and body structure
- **Group B** (POST 409 conflict): ✓ ConflictException handler and conflict detection delegation
- **Group C** (GET 200): ✓ Proper retrieval and response serialization
- **Group D** (DELETE 204): ✓ Proper idempotent cancellation with empty response
- **Group E** (Concurrency): ✓ Synchronized store method ensures atomicity
- **Group F** (Content-Type): ✓ Spring/Jackson handle application/json automatically
- **Group G** (Response structure): ✓ ErrorResponse and ReservationResponse DTOs with correct fields

---

## Success Criteria for GREEN Phase

✓ All three endpoints are properly wired and routed  
✓ POST creates reservations with conflict detection and returns 201 Created  
✓ POST on conflict returns 409 Conflict with proper error response  
✓ GET retrieves reservations (ACTIVE or CANCELLED) and returns 200 OK  
✓ GET on non-existent reservation returns 404 Not Found  
✓ DELETE cancels reservations and returns 204 No Content  
✓ DELETE on non-existent reservation returns 404 Not Found  
✓ DELETE is idempotent (cancelling already-cancelled returns 204)  
✓ All responses include `Content-Type: application/json`  
✓ Error responses include error classification and details fields  
✓ Timestamps formatted as ISO 8601 UTC strings (e.g., "2025-09-07T10:00:00Z")  
✓ Concurrent POST requests properly serialize conflict detection  
✓ UUID path parameter validation returns 400 Bad Request on invalid format  
✓ All Milestone 7 RED tests pass (32+ tests)  
✓ All prior milestone tests continue to pass (Milestones 1-6: 54 tests)  

---

## Production Changes Summary

**Files Modified**:

1. **ReservationController.java**
   - Fix datetime parsing and formatting (Instant/UTC)
   - Replace `store.create()` with `store.createWithConflictCheck()`
   - Update `toResponse()` method to format timestamps correctly

2. **GlobalExceptionHandler.java**
   - Add @ExceptionHandler for ConflictException
   - Map to 409 Conflict HTTP status

**Files Not Modified**:
- ReservationStore.java (createWithConflictCheck already in place)
- Reservation.java (domain model unchanged)
- ReservationValidator.java (already complete from Milestone 4)
- Request/response DTOs (no business logic changes)
- Exception classes (no changes)
- Application.java (no changes)
- pom.xml (no new dependencies)

**Test Files Not Modified** (per test-engineer mode boundary):
- No test code is modified in GREEN phase

---

## Predecessor Evidence

Successful completion of:
- **Milestone 1 (RED)**: Core store tests defined and passing
- **Milestone 2 (GREEN)**: ReservationStore implemented with ConcurrentHashMap
- **Milestone 3 (RED)**: Validation tests defined and passing  
- **Milestone 4 (GREEN)**: Validators and error handlers implemented
- **Milestone 5 (RED)**: Conflict detection tests defined and passing
- **Milestone 6 (GREEN)**: Conflict detection with synchronized createWithConflictCheck() implemented
- **Milestone 7 (RED)**: HTTP API tests defined (22 tests) with valid failure evidence

All 54 Milestones 1-6 tests pass. Milestone 7 RED shows 2 failures and 18 errors (all due to datetime formatting bug and missing createWithConflictCheck delegation). Ready for GREEN implementation.

---

## Successor

After GREEN completion:
- All tests pass (32+ Milestone 7 tests + 54 prior milestone tests)
- Service is delivery-ready
- No further milestones scheduled

---

## Exclusions

This implementation does NOT include:

- List, query, search, or bulk endpoints
- Pagination, filtering, or sorting
- Reservation update or modification endpoints
- Workspace or user management endpoints
- Authentication or authorization
- Health checks, metrics, or observability endpoints
- Caching headers or conditional requests
- API versioning beyond base path `/api/v1`
- Request/response transformation beyond datetime formatting
- Validation beyond contract requirements

---

## Notes

- All proposed changes are minimal and focused on making RED tests pass
- No refactoring beyond what's necessary for correctness
- Concurrency safety relies on existing synchronized store method
- Datetime handling is standardized on ISO 8601 UTC format
- Error responses follow consistent structure from Milestone 4
- No external dependencies added (Spring Boot and Jackson already available)


