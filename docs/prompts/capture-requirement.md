Create docs/requirements.md for a new application named
workspace-reservation-service.

This task is REQUIREMENTS CAPTURE only.

Apply the requirements-analysis and prompt-driven-development skills.

Do not create Plan.md.
Do not create an API contract.
Do not create an Implementation Plan.
Do not create tests or source code.
Do not introduce architecture decisions beyond these explicit requirements.

Explicit requirements:

Application:
- Java 17.
- Spring Boot 3.
- Maven.
- REST API.
- Reservation state is maintained in application memory only.
- Losing reservation state after application restart is acceptable.
- No database.
- No JPA.
- No Flyway.
- No messaging.
- No cache.
- No external service calls.
- Authentication and authorization are out of scope.

Reservation:
- id: server-generated UUID.
- workspaceId: string.
- userId: string.
- startTime: UTC.
- endTime: UTC.
- status: ACTIVE or CANCELLED.

Create reservation:
POST /api/v1/reservations

- workspaceId, userId, startTime and endTime are required.
- startTime must be before endTime.
- New reservations are ACTIVE.
- ACTIVE reservations for the same workspace must not overlap.
- Reservations for different workspaces may overlap.
- CANCELLED reservations do not block new reservations.
- Return 201 when created.
- Return 400 for invalid request data.
- Return 409 when an ACTIVE reservation overlaps an existing ACTIVE
  reservation for the same workspace.

Get reservation:
GET /api/v1/reservations/{id}

- Return 200 when found.
- Return 404 when not found.

Cancel reservation:
DELETE /api/v1/reservations/{id}

- ACTIVE becomes CANCELLED.
- Cancelling an already CANCELLED reservation is idempotent.
- Return 204 when cancellation succeeds.
- Return 404 when the reservation does not exist.
- A cancelled reservation must no longer block future reservations.

Concurrency:
- Concurrent create requests must not successfully create two overlapping
  ACTIVE reservations for the same workspace.

If any material requirement is ambiguous or missing, ask focused questions
and STOP. Do not create docs/requirements.md until the ambiguity is resolved.

Create only docs/requirements.md.