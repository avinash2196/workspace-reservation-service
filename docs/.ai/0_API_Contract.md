# Workspace Reservation Service - API Contract (Milestone 0)

**Status**: Pending Approval

**Traceability**: Approved Requirements [docs/requirements.md](../requirements.md) § API Endpoints, Conflict Detection, Concurrency Guarantee

---

## Overview

This contract defines the externally observable HTTP API for the Workspace Reservation Service. The contract establishes HTTP method, path, request/response schemas, status codes, error handling, and concurrency guarantees derived directly from approved requirements.

---

## Base URL and Versioning

- **Base URL**: `/api/v1`
- **Content-Type**: `application/json` for all request and response bodies
- **Character Encoding**: UTF-8

---

## Common Schemas

### Reservation Object

Represents a reservation after successful creation or retrieval.

```json
{
  "id": "uuid",
  "workspaceId": "string",
  "userId": "string",
  "startTime": "string (ISO 8601 UTC datetime)",
  "endTime": "string (ISO 8601 UTC datetime)",
  "status": "enum (ACTIVE | CANCELLED)"
}
```

**Field Definitions**:
- `id`: Server-generated UUID, immutable after creation
- `workspaceId`: Identifier for the workspace being reserved
- `userId`: Identifier for the user making the reservation
- `startTime`: Inclusive start of the reservation in UTC
- `endTime`: Inclusive end of the reservation in UTC
- `status`: Current state of the reservation (ACTIVE or CANCELLED)

### Error Response Object

Used for all error responses (400, 404, 409).

```json
{
  "error": "string",
  "details": "string"
}
```

**Field Definitions**:
- `error`: Short error classification (e.g., "VALIDATION_ERROR", "NOT_FOUND", "CONFLICT")
- `details`: Descriptive information to aid client debugging

---

## Endpoints

### 1. Create Reservation

**Method**: `POST`

**Path**: `/api/v1/reservations`

#### Request

**Headers**:
- `Content-Type: application/json`

**Body**:

```json
{
  "workspaceId": "string",
  "userId": "string",
  "startTime": "string (ISO 8601 UTC datetime)",
  "endTime": "string (ISO 8601 UTC datetime)"
}
```

**Required Fields**: `workspaceId`, `userId`, `startTime`, `endTime`

#### Validation

- All four fields are required.
- `startTime` must be strictly before `endTime`.
- Invalid or missing data must result in **400 Bad Request**.

#### Response: 201 Created

**Status Code**: `201 Created`

**Headers**:
- `Content-Type: application/json`
- `Location: /api/v1/reservations/{id}` (recommended, optional)

**Body**:

```json
{
  "id": "uuid",
  "workspaceId": "string",
  "userId": "string",
  "startTime": "string (ISO 8601 UTC datetime)",
  "endTime": "string (ISO 8601 UTC datetime)",
  "status": "ACTIVE"
}
```

**Guarantees**:
- The reservation is created with `status=ACTIVE`.
- The server-generated `id` is a valid UUID.

#### Response: 400 Bad Request

**Status Code**: `400 Bad Request`

**Body**:

```json
{
  "error": "VALIDATION_ERROR",
  "details": "string describing the validation failure"
}
```

**Triggers**:
- Missing required field (`workspaceId`, `userId`, `startTime`, or `endTime`)
- Invalid field format or type
- `startTime` is not strictly before `endTime`

#### Response: 409 Conflict

**Status Code**: `409 Conflict`

**Body**:

```json
{
  "error": "CONFLICT",
  "details": "An ACTIVE reservation already exists for this workspace during the requested time range"
}
```

**Conflict Condition**:
- An ACTIVE reservation already exists for the same `workspaceId` such that:
  - The new reservation's `[startTime, endTime]` overlaps with the existing reservation's time range.
  - Overlap is defined as: `new_startTime < existing_endTime AND existing_startTime < new_endTime`
  - Adjacent reservations (e.g., 10:00-11:00 and 11:00-12:00) do **not** overlap and do **not** conflict.
- CANCELLED reservations do **not** block new reservations, regardless of time overlap.
- Reservations for different workspaces do not interfere with conflict detection.

#### Concurrency Guarantee

**Contract Observable Behavior**:
Concurrent requests to `POST /api/v1/reservations` must not successfully create two overlapping ACTIVE reservations for the same `workspaceId`. If two concurrent requests would create overlapping ACTIVE reservations for the same workspace, one must succeed with **201 Created** and the other must receive **409 Conflict**.

---

### 2. Get Reservation

**Method**: `GET`

**Path**: `/api/v1/reservations/{id}`

#### Path Parameters

- `id`: UUID of the reservation to retrieve (required)

#### Response: 200 OK

**Status Code**: `200 OK`

**Headers**:
- `Content-Type: application/json`

**Body**:

```json
{
  "id": "uuid",
  "workspaceId": "string",
  "userId": "string",
  "startTime": "string (ISO 8601 UTC datetime)",
  "endTime": "string (ISO 8601 UTC datetime)",
  "status": "enum (ACTIVE | CANCELLED)"
}
```

**Guarantees**:
- The returned reservation is the one with the matching `id`.
- The reservation may have `status=ACTIVE` or `status=CANCELLED`.

#### Response: 404 Not Found

**Status Code**: `404 Not Found`

**Body**:

```json
{
  "error": "NOT_FOUND",
  "details": "Reservation with id '{id}' not found"
}
```

**Triggers**:
- No reservation exists with the given `id`.

---

### 3. Cancel Reservation

**Method**: `DELETE`

**Path**: `/api/v1/reservations/{id}`

#### Path Parameters

- `id`: UUID of the reservation to cancel (required)

#### Behavior

- If the reservation exists and `status=ACTIVE`, the status changes to `CANCELLED`.
- If the reservation exists and `status=CANCELLED`, the operation is idempotent and succeeds without error.
- A cancelled reservation no longer blocks future reservations in conflict detection.

#### Response: 204 No Content

**Status Code**: `204 No Content`

**Body**: Empty (no response body)

**Triggers**:
- The reservation with the given `id` exists (regardless of current status).

**Guarantees**:
- After a successful 204 response, `GET /api/v1/reservations/{id}` returns the reservation with `status=CANCELLED`.
- The operation is idempotent: cancelling an already-cancelled reservation succeeds.

#### Response: 404 Not Found

**Status Code**: `404 Not Found`

**Body**:

```json
{
  "error": "NOT_FOUND",
  "details": "Reservation with id '{id}' not found"
}
```

**Triggers**:
- No reservation exists with the given `id`.

---

## Error Handling Summary

| Status Code | Error Classification | Conditions |
|-------------|----------------------|-----------|
| 400 Bad Request | `VALIDATION_ERROR` | Missing/invalid fields, invalid field types, invalid time range |
| 404 Not Found | `NOT_FOUND` | Reservation does not exist |
| 409 Conflict | `CONFLICT` | ACTIVE reservation overlaps with existing ACTIVE reservation for same workspace |

---

## Immutability and State Transitions

- Reservation properties (`workspaceId`, `userId`, `startTime`, `endTime`) are immutable after creation.
- The only permitted state transition is `ACTIVE` → `CANCELLED` (via DELETE endpoint).
- There is no update or modification endpoint.

---

## No List Endpoint

The API does not provide a bulk list or query endpoint for reservations. Access is individual by id only.

---

## Timestamp Format and Precision

- All timestamps use ISO 8601 format with UTC timezone.
- Example: `2025-09-07T14:30:00Z`
- Datetime comparison and validation must account for exact datetime equality.

---

## Non-Blocking Implementation Details

The following implementation decisions are not specified by this contract and remain flexible for the GREEN phase:

- Exact error message text (error classification and HTTP status code are contract; wording is implementation).
- UUID generation method and library.
- JSON serialization library and field naming conventions (schema structure is contract).
- HTTP header handling beyond `Content-Type`.
- Internal concurrency mechanism (synchronized methods, locks, atomic operations, etc.).

---

## Exclusions

The following are explicitly out of scope and must not appear in the externally observable API:

- Bulk list or query endpoints
- Pagination or filtering
- Reservation update or modification endpoints
- Workspace or user management endpoints
- Authentication or authorization headers
- Health checks, metrics, or observability endpoints
- Caching headers (ETag, Cache-Control, etc.)

---

## Traceability

| Contract Element | Requirement Source |
|------------------|-------------------|
| POST /api/v1/reservations, 201 response, request schema | requirements.md § Create Reservation |
| POST /api/v1/reservations, 400 Validation | requirements.md § Create Reservation, Validation Rules |
| POST /api/v1/reservations, 409 Conflict | requirements.md § Create Reservation, Conflict Detection |
| POST /api/v1/reservations, Concurrency Guarantee | requirements.md § Create Reservation, Concurrency Guarantee |
| GET /api/v1/reservations/{id}, 200/404 responses | requirements.md § Get Reservation |
| DELETE /api/v1/reservations/{id}, 204/404 responses | requirements.md § Cancel Reservation |
| DELETE /api/v1/reservations/{id}, Idempotence | requirements.md § Cancel Reservation, Behavior |
| Overlap Definition (s1 < e2 AND s2 < e1) | requirements.md § Conflict Detection, Overlap Definition |
| CANCELLED reservations do not block | requirements.md § Conflict Detection |
| Different workspaces do not interfere | requirements.md § Constraints and Guarantees, Workspace Isolation |
| Reservation properties immutable after creation | requirements.md § Constraints and Guarantees, Immutability |
| In-memory storage, no persistence | requirements.md § Technology Stack, State Loss |


