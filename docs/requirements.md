# Workspace Reservation Service - Requirements

## Application Overview

The Workspace Reservation Service is a Java Spring Boot REST API that manages reservations for shared workspaces. The application maintains reservation state in application memory only, with no persistence or external integrations.

## Technology Stack

- **Runtime**: Java 17
- **Framework**: Spring Boot 3
- **Build Tool**: Maven
- **HTTP Protocol**: REST API with JSON payloads
- **Data Storage**: In-memory only
- **State Loss**: Acceptable on application restart

## Scope Exclusions

The following are **explicitly out of scope** and must not be introduced:

- Production database or persistence layer
- JPA or ORM framework
- Flyway or database migrations
- Message queue or event publishing
- Caching layer
- External service integrations
- Authentication and authorization
- User management or identity verification

## Reservation Model

A reservation represents a time-based booking of a workspace by a user.

### Properties

| Property | Type | Notes |
|----------|------|-------|
| id | UUID | Server-generated, immutable |
| workspaceId | String | Identifier for the workspace being reserved |
| userId | String | Identifier for the user making the reservation |
| startTime | UTC DateTime | Inclusive start of the reservation |
| endTime | UTC DateTime | Inclusive end of the reservation |
| status | Enum: ACTIVE, CANCELLED | Current state of the reservation |

## API Endpoints

### 1. Create Reservation

**Endpoint**: `POST /api/v1/reservations`

**Request Body**:
```json
{
  "workspaceId": "string",
  "userId": "string",
  "startTime": "UTC datetime",
  "endTime": "UTC datetime"
}
```

**Validation Rules**:
- `workspaceId`, `userId`, `startTime`, and `endTime` are required.
- `startTime` must be strictly before `endTime`.
- All invalid or missing data results in **400 Bad Request**.

**Conflict Detection**:
- New reservations are created with status `ACTIVE`.
- An ACTIVE reservation cannot be created if an ACTIVE reservation already exists for the same `workspaceId` that overlaps in time.
- **Overlap Definition**: Two time ranges `[s1, e1]` and `[s2, e2]` overlap if `s1 < e2` AND `s2 < e1`. Reservations that end exactly when another starts are **not** considered overlapping (e.g., 10:00-11:00 and 11:00-12:00 do not conflict).
- Reservations for different workspaces may overlap without restriction.
- CANCELLED reservations do **not** block new reservations, regardless of time overlap.
- Conflict detection returns **409 Conflict**.

**Concurrency Guarantee**:
- Concurrent create requests must not successfully create two overlapping ACTIVE reservations for the same workspace.

**Response**:
- **201 Created** on success with the created reservation (including server-generated id and status=ACTIVE).
- **400 Bad Request** for invalid or missing required fields, or if startTime is not before endTime.
- **409 Conflict** if an ACTIVE reservation overlaps an existing ACTIVE reservation for the same workspace.

**Error Response Format**:
Error responses include a JSON body with descriptive information to aid client debugging.

---

### 2. Get Reservation

**Endpoint**: `GET /api/v1/reservations/{id}`

**Path Parameters**:
- `id`: UUID of the reservation to retrieve.

**Response**:
- **200 OK** with the reservation object if found.
- **404 Not Found** if no reservation exists with the given id.

---

### 3. Cancel Reservation

**Endpoint**: `DELETE /api/v1/reservations/{id}`

**Path Parameters**:
- `id`: UUID of the reservation to cancel.

**Behavior**:
- If the reservation exists and status is `ACTIVE`, change status to `CANCELLED`.
- If the reservation exists and status is already `CANCELLED`, the operation is idempotent and succeeds without error.
- A cancelled reservation must no longer block future reservations for overlap detection.

**Response**:
- **204 No Content** on success (with no response body).
- **404 Not Found** if no reservation exists with the given id.

---

## Constraints and Guarantees

1. **No List Endpoint**: The API does not provide a bulk list or query endpoint for reservations. Access is individual by id only.

2. **Immutability**: Reservation properties (other than status) are immutable after creation.

3. **Idempotent Cancel**: Cancelling an already-cancelled reservation succeeds without error.

4. **Workspace Isolation**: Overlap detection applies per-workspace only. Concurrent requests for different workspaces do not interfere with each other.

5. **Time Precision**: All timestamps use UTC. Comparison and validation must account for exact datetime equality.

6. **In-Memory Volatility**: Reservation state is lost on application shutdown or restart. No recovery or persistence is required.

## Non-Blocking Implementation Details

The following details are implementation decisions not addressed by explicit requirements and remain flexible:

- **Concurrency Mechanism**: The specific locking strategy (e.g., synchronized methods, ReentrantReadWriteLock, atomic operations) is an implementation detail.
- **Error Message Format**: The exact structure and wording of error response bodies is flexible, provided the HTTP status code and semantic meaning remain correct.
- **Reservation ID Format**: While the requirement specifies UUID, the format and generation method are implementation details.
- **HTTP Header Handling**: Standard HTTP headers (e.g., Content-Type: application/json) are assumed but not explicitly specified.

## Approval Boundaries

This requirements document establishes the contract for:
- Behavior verification through tests (RED phase)
- Production implementation scope (GREEN phase)
- Optional refactoring and optimization (REFACTOR phase)

Further work proceeds through the prompt-driven-development lifecycle with human review between each phase.

