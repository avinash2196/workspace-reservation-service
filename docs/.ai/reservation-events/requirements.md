# reservation-events — Requirements

## Work Item

`reservation-events`

## Goal

When a reservation is created, the service publishes a `ReservationCreated` event to Kafka so the
notifications team can email the user a confirmation.

This work item ends at a published event that matches the approved external contract. Sending the
email is owned by the notifications team and is outside this work item.

## Authority and Status

- This document is authoritative for the `reservation-events` work item.
- The product-level `docs/requirements.md` is read-only context for this work item.
- Label prefixes used here (`RE-R`, `RE-O`, `RE-C`, `RE-X`, `RE-D`) are specific to this work item.
- Status: **approved** by human review (2026-09-28). This document does not by itself authorize
  any external contract, Implementation Plan, test, or production change.

## Relationship to Product-Level Requirements

The product-level `docs/requirements.md` currently lists under **Scope Exclusions**:
"Message queue or event publishing" and "External service integrations".

- **RE-R1** — This work item is an approved enhancement that lifts the product-level exclusion
  "Message queue or event publishing" **only** for publishing `ReservationCreated` events to Kafka.
- **RE-R2** — Every other product-level scope exclusion remains in force, including all other
  external service integrations, persistence, JPA/ORM, migrations, caching, authentication and
  authorization, and user management. In particular, "External service integrations" remains
  excluded except for the single Kafka publish approved by RE-R1.
- **RE-R3** — The product-level statement that the application has "no persistence or external
  integrations" is made out of date by RE-R1. The user has stated they will update
  `docs/requirements.md` themselves. This work item must not edit that file.

No other material conflict between this document and `docs/requirements.md` has been identified.

## Repository-Confirmed Current State

Confirmed by inspection of the repository at the time of writing:

- The only reservation creation path is `POST /api/v1/reservations`, handled by
  `ReservationController.createReservation` (`src/main/java/com/example/workspace/reservation/controller/ReservationController.java:33`).
  On success it returns **201 Created** with the created reservation.
- Creation succeeds when `ReservationStore.createWithConflictCheck` returns without throwing
  `ConflictException` (`src/main/java/com/example/workspace/reservation/store/ReservationStore.java:48`).
  That method is `synchronized` and performs an atomic conflict check followed by the store write.
- A created reservation carries `id` (UUID), `workspaceId`, `userId`, `startTime`, `endTime`, and
  `status` (`src/main/java/com/example/workspace/reservation/domain/Reservation.java`). Times are
  held as `LocalDateTime` treated as UTC; the HTTP layer formats them with `ISO_INSTANT`
  (`ReservationController.java:109`).
- Reservation state is held in an in-memory `ConcurrentHashMap` inside one JVM
  (`ReservationStore.java:16`). State is lost on restart.
- Cancellation exists as `DELETE /api/v1/reservations/{id}` and sets status `CANCELLED`.
- `pom.xml` declares only `spring-boot-starter`, `spring-boot-starter-web`, and
  `spring-boot-starter-test`. There is **no** Kafka, messaging, Actuator, Micrometer, or other
  metrics dependency today.
- `src/main/resources/application.properties` contains only `server.port=8081`. There is no broker
  configuration.
- Approved verification commands are `mvn test` and `mvn verify` (`CLAUDE.md`).

## Functional Requirements

- **RE-R4** — When a reservation is successfully created, the service publishes one
  `ReservationCreated` event to Kafka.
- **RE-R5** — A publish is attempted only for a reservation that was actually created. No event is
  published when creation fails validation (400) or conflict detection (409).
- **RE-R6** — `ReservationCreated` is the only event in scope. No cancellation event and no other
  event is published by this work item.
- **RE-R7** — The event carries `userId` only as user identification. It must not carry an email
  address or any other personal data. The notifications team resolves `userId` to an email address.

## Delivery Semantics

- **RE-R8** — Delivery is best-effort.
- **RE-R9** — A successfully created reservation still returns **201 Created** with its normal
  response body even when publishing the event fails. Publish failure must not change the HTTP
  status, the response body, or the stored reservation state.
- **RE-R10** — Failed publishes are not retried durably. Unsent events may be lost when the
  application restarts.
- **RE-R11** — Occasional duplicate events are acceptable. The consumer deduplicates by reservation
  id, so at-least-once-style duplication is permitted and exactly-once delivery is not required.
- **RE-R12** — When the broker is slow or unavailable, behavior is as stated in RE-R8 through
  RE-R11: the created reservation still succeeds, and the failure is recorded per RE-R16.

## Operational Characteristics

Every characteristic was put to the user. Answers, including explicit "not required" exclusions:

| ID | Characteristic | Answer |
|----|----------------|--------|
| RE-O1 | Deployment topology | Single running instance. |
| RE-O2 | Consistency and concurrency | Best-effort publish; duplicates acceptable (consumer deduplicates by reservation id); unsent events may be lost on restart. No ordering guarantee is required of this work item — ordering is a decision deferred to the CONTRACT milestone (RE-C1). |
| RE-O3 | Dependency failure | The broker may be slow or unavailable. The application must start and run with no broker present, and `mvn test` and `mvn verify` must pass with no broker present. |
| RE-O4 | Observability | Failed publishes must be logged and counted (RE-R16). Nothing else is required for this work item. |
| RE-O5 | Scale | **Not required for this work item.** No user count, request rate, data volume, or growth target is specified or to be designed for. |
| RE-O6 | Availability and latency | **Not required for this work item.** No uptime target, latency target, or deployment-downtime tolerance is specified. |
| RE-O7 | Security | **Not required for this work item.** No authentication, authorization, transport security, or secret-handling requirement is introduced beyond RE-R7 (no PII in the event). |

## Observability Requirements

- **RE-R16** — A failed publish must be logged and counted. Nothing further is required: no metrics
  backend, exporter, dashboard, alert, trace, or monitoring-stack integration is required by this
  work item.

## Testing Requirements

- **RE-R17** — Publish behavior is verified with an in-process test double. No Docker container, no
  embedded broker, and no real Kafka broker is used in tests.
- **RE-R18** — `mvn test` and `mvn verify` must pass with no broker present (see RE-O3).

## Decisions Deferred to the CONTRACT Milestone

These are deliberately unresolved here and are owned by the CONTRACT milestone. They must not be
fixed by the Plan or presumed by any Implementation Plan.

- **RE-C1** — Topic name, message key, serialization format, event schema (including the exact field
  set beyond the RE-R7 constraint), schema versioning, and ordering guarantees.

## Explicit Exclusions

- **RE-X1** — Sending the confirmation email, and any other notification delivery, is out of scope.
- **RE-X2** — No cancellation event or any event other than `ReservationCreated` (RE-R6).
- **RE-X3** — No durable retry, outbox, persistent queue, or dead-letter handling (RE-R10).
- **RE-X4** — No transactional or exactly-once publish guarantee (RE-R11).
- **RE-X5** — No persistence, JPA/ORM, migrations, caching, authentication, authorization, user
  management, or any external integration other than the approved Kafka publish (RE-R2).
- **RE-X6** — No email address or other personal data in the event (RE-R7).
- **RE-X7** — No scale, availability, latency, or security work (RE-O5, RE-O6, RE-O7).
- **RE-X8** — No change to the existing HTTP contract of the three existing endpoints (RE-R9).
- **RE-X9** — No metrics backend, exporter, dashboard, alert, or tracing (RE-R16).
- **RE-X10** — No Docker or embedded broker in tests (RE-R17).
- **RE-X11** — No multi-instance, clustering, or distributed-coordination behavior (RE-O1).

## Non-Blocking Boundaries

These do not affect the correctness or approved scope of this document and are left to later phases:

- **RE-D1** — Where the publish is triggered from within the existing creation path, and the
  internal structure used to do it.
- **RE-D2** — The mechanism of the failure count required by RE-R16, and whether it is exposed
  anywhere beyond the application. Nothing beyond "logged and counted" is required.
- **RE-D3** — Log level, log message wording, and log field naming for a failed publish.
- **RE-D4** — Broker connection configuration keys and their default values, subject to RE-O3
  (the application must start and run, and the build must pass, with no broker present).
- **RE-D5** — Client library choice and version within the approved Java 17 / Spring Boot / Maven
  stack.

## Acceptance Criteria

1. A successful `POST /api/v1/reservations` results in one `ReservationCreated` event published to
   Kafka that matches the approved external contract (RE-R4).
2. A rejected create (400 or 409) publishes no event (RE-R5).
3. A create whose publish fails still returns 201 with its normal response body, and the reservation
   remains created (RE-R9).
4. A failed publish is logged and counted (RE-R16).
5. The event contains `userId` and no email address or other personal data (RE-R7).
6. The application starts and runs, and `mvn test` and `mvn verify` pass, with no broker present
   (RE-O3, RE-R18).
7. Publish behavior is verified with an in-process test double only (RE-R17).
8. No capability listed under Explicit Exclusions is introduced.

## Source of Decisions

Requirements RE-R1 through RE-R18, RE-O1 through RE-O7, RE-C1, and RE-X1 through RE-X11 are taken
from the user's stated decisions on the requirements-review clarification questions:

| Question | Requirements recorded |
|----------|-----------------------|
| Q1 | RE-R1, RE-R2, RE-R3, RE-X5 |
| Q2 / Q3 / Q5 | RE-R8, RE-R9, RE-R10, RE-X3, RE-X8 |
| Q4 | RE-R11, RE-X4 |
| Q6 | RE-R6, RE-X2 |
| Q7 / Q9 | RE-C1 |
| Q8 | RE-R7, RE-X6 |
| Q10 | RE-O1, RE-X11 |
| Q11 | RE-O5, RE-X7 |
| Q12 | RE-O6, RE-X7 |
| Q13 | RE-O3, RE-R12, RE-R18 |
| Q14 | RE-O4, RE-R16, RE-X9 |
| Q15 | RE-O7, RE-X7 |
| Q16 | RE-R17, RE-X10 |
| Q17 | RE-X1 |

No requirement in this document was inferred from framework defaults, industry convention,
repository convention, or preference.
