# reservation-events — Plan

## Work Item

`reservation-events`

## Authority and Status

- This Plan defines approved scope, milestone sequence, requirement ownership, success criteria, and
  execution status for the `reservation-events` work item.
- Status: **approved** by human review (2026-09-28). No milestone is verified or complete. Each
  milestone still requires its own human review; plan approval authorizes only RE-M1 to start.
- Label prefixes introduced here are `RE-M` (milestones), `RE-S` (plan-level success criteria), and
  `RE-K` (risks). They do not reuse any label defined by the artifacts this Plan references
  (`RE-R`, `RE-O`, `RE-C`, `RE-X`, `RE-D` in the work item's requirements; the numeric
  `Milestone N` labels of another work item's Plan are not referenced by this Plan).

## Authoritative Inputs

| Input | Role |
|-------|------|
| [`docs/.ai/reservation-events/requirements.md`](requirements.md) | Authoritative requirements for this work item |
| [`docs/requirements.md`](../../requirements.md) | Product-level context, read-only; this work item must not edit it (RE-R3) |
| `CLAUDE.md` | Approved technology stack and approved verification commands |

The work item's requirements record the only identified conflict with the product-level document
(RE-R1, RE-R2, RE-R3) and how it is resolved. No further material conflict was found while creating
this Plan.

## Approved Scope

When a reservation is successfully created, the service publishes one `ReservationCreated` event to
Kafka, as a best-effort publish that cannot change the outcome of the creation request, and a failed
publish is logged and counted inside the application.

Scope is exactly RE-R1 through RE-R18, RE-O1 through RE-O7, and RE-C1 as recorded in the approved
requirements. Nothing else is in scope.

## Current State

Established by inspection of the repository while creating this Plan; it matches the
Repository-Confirmed Current State recorded in the approved requirements.

Existing behavior this work item affects:

- A single reservation-creation operation is the only path that creates a reservation. On success it
  returns the created reservation in its existing success response.
- Creation succeeds only when the atomic conflict check completes without rejecting the request;
  rejection by validation or by conflict detection leaves no reservation created.
- Reservation state is held in application memory within one running instance and is lost on
  restart. Cancellation exists and marks a reservation cancelled.
- The externally observable behavior of the existing operations is defined by the product-level
  requirements and by the approved external contract of the earlier `initial-build` work item. This
  work item changes none of it (RE-X8).

Existing tests that cover the affected behavior:

- Automated tests cover creation, retrieval, and cancellation at the in-memory state boundary,
  including conflict detection and concurrent creation.
- Automated tests cover the creation operation's success and rejection behavior at the HTTP
  boundary, including validation rejection and conflict rejection.
- These tests must continue to pass unchanged throughout this work item.

Prerequisites that do not exist today:

- The build declares no messaging, broker-client, metrics, or monitoring dependency.
- The application's runtime configuration contains no broker connection configuration.
- There is no event publishing capability and no event type of any kind.

## Out of Scope

Only the exclusions stated by the approved requirements:

- Sending the confirmation email or any other notification delivery (RE-X1).
- Any event other than `ReservationCreated`, including a cancellation event (RE-X2, RE-R6).
- Durable retry, outbox, persistent queue, or dead-letter handling (RE-X3, RE-R10).
- Transactional or exactly-once publish guarantees (RE-X4, RE-R11).
- Persistence, JPA/ORM, migrations, caching, authentication, authorization, user management, and
  every external integration other than the approved Kafka publish (RE-X5, RE-R2).
- Any email address or other personal data in the event (RE-X6, RE-R7).
- Scale, availability, latency, and security work (RE-X7, RE-O5, RE-O6, RE-O7).
- Any change to the externally observable behavior of the three existing operations (RE-X8, RE-R9).
- Any metrics backend, exporter, dashboard, alert, or tracing; the failure count stays internal to
  the application (RE-X9, RE-R16, RE-D2).
- Docker, an embedded broker, or a real broker in tests (RE-X10, RE-R17).
- Multi-instance, clustering, or distributed-coordination behavior (RE-X11, RE-O1).
- Editing `docs/requirements.md` (RE-R3). Product-level statements this work item makes out of date
  are listed at Final Review for the user to update.

## Milestone Sequence

One strictly linear sequence. Each milestone has exactly one predecessor.

| Milestone | Type | Title | Predecessor | Implementation Plan |
|-----------|------|-------|-------------|---------------------|
| RE-M1 | CONTRACT | Reservation event external contract | Plan approval | None (the contract artifact is the reviewed deliverable) |
| RE-M2 | FOUNDATION | Messaging prerequisites for broker-free execution | RE-M1 | Required |
| RE-M3 | RED | Event publishing behavior — tests | RE-M2 | Required |
| RE-M4 | GREEN | Event publishing behavior | RE-M3 | Required |
| RE-M5 | RED | Creation-path integration — tests | RE-M4 | Required |
| RE-M6 | GREEN | Creation-path integration | RE-M5 | Required |

No REFACTOR milestone is planned: no cleanup is justified by current repository evidence. If a GREEN
milestone reveals justified behavior-preserving cleanup, that requires explicit replanning and human
review, not an unplanned milestone.

### Decomposition Boundaries and Reasons

- **CONTRACT first (RE-M1).** The published event is a stable consumer-facing interface owned jointly
  with the notifications team, and the requirements defer RE-C1 to it. No repository-changing
  milestone may start before that externally observable behavior is approved.
- **FOUNDATION before the first RED (RE-M2).** Justified below in RE-M2; it exists only because
  executable prerequisites are genuinely missing, not because production types are absent.
- **Two RED/GREEN sequences rather than one feature-wide pair.** The work has two independently
  testable boundaries:
  - *Publishing behavior* (RE-M3/RE-M4): what is published, that it conforms to the approved
    contract, and that a failure at the broker boundary is absorbed, logged, and counted. This is
    verifiable in isolation with an in-process test double standing in for the broker, without
    involving the creation path.
  - *Creation-path integration* (RE-M5/RE-M6): that exactly one publish is attempted per successful
    creation, none for a rejected creation, and that a publish failure changes neither the creation
    response nor the stored reservation. This is verifiable at the creation path with the publishing
    capability doubled, and it is the boundary where the existing HTTP behavior must be proven
    unchanged.
  The boundary is drawn there because the two concerns fail for different reasons, are reviewed
  against different requirements, and the second can only be tested meaningfully once the first
  exists. A single pair would bundle broker-failure handling and existing-behavior preservation into
  one review, and a finer split (one pair per internal component) would prescribe internal structure
  that RE-D1 leaves open.
- **Sequence order.** Publishing behavior precedes creation-path integration so that RE-M5's tests
  have an existing publishing capability to double, satisfying the prerequisite rule without a second
  FOUNDATION milestone.

---

### RE-M1 — CONTRACT: Reservation event external contract

**Type**: CONTRACT

**Predecessor**: Plan approval

**Successor**: RE-M2

**Goal**: Define and have approved the externally observable contract of the `ReservationCreated`
event so that the notifications team can consume it and so that no later milestone has to decide it.

**Deliverable**: `docs/.ai/reservation-events/API-Contract.md`. No executable artifact changes, so
this milestone has no Implementation Plan.

**Decisions this milestone owns** — exactly the decisions the requirements defer to it (RE-C1):

- topic name;
- message key;
- serialization format;
- event schema, including the exact field set;
- schema versioning;
- ordering guarantees.

The contract must honor RE-R7 (the event carries `userId` as the only user identification and no
email address or other personal data) and RE-R6 (`ReservationCreated` is the only event).

**Explicit Exclusions**:

- No decision beyond the RE-C1 list and the event's externally observable behavior — no
  documentation tooling, no registry, no additional capability.
- No change to the existing operations' external contract (RE-X8).
- No internal design, no dependency choice, no configuration keys (RE-D1, RE-D4, RE-D5).
- No consumer-side behavior; the notifications team owns consumption and deduplication (RE-X1).

**Success Criteria**:

- Every RE-C1 decision is resolved explicitly in the contract artifact.
- The defined event carries no email address or other personal data (RE-R7).
- No event other than `ReservationCreated` is defined (RE-X2, RE-R6).
- The contract states nothing that changes the existing operations' external behavior (RE-X8).
- The contract artifact is approved by human review.

---

### RE-M2 — FOUNDATION: Messaging prerequisites for broker-free execution

**Type**: FOUNDATION

**Predecessor**: RE-M1

**Successor**: RE-M3

**Serves**: RE-M3 and RE-M5.

**Why this milestone is required** (Plan Content Rule 11, evaluated against the repository state
projected after RE-M1, which changes no executable artifact):

- Publishing to Kafka (RE-R4) requires a broker-client dependency on the build; the build declares
  none today.
- RE-M3's tests must stand in for the broker in process (RE-R17) and must assert that a publish
  carrying the approved event payload reaches the broker boundary and that a failure at that boundary
  is absorbed. They therefore need the client's publishing types on the test classpath.
- RE-O3 and RE-R18 require the application to start and run, and `mvn test` and `mvn verify` to pass,
  with no broker present. Once a broker client is on the classpath, that is a dependency and
  configuration property of the build and runtime configuration, and it must hold before any test
  references the client.
- A RED milestone cannot add dependencies, build configuration, or runtime configuration, and GREEN
  comes after RED. So no later milestone can supply these prerequisites in time.

This milestone is **not** justified by the absence of a publishing capability or an event type. Those
are absent behavior that RE-M3 is intended to drive and RE-M4 to deliver.

**Goal**: Establish the minimum prerequisites that let RE-M3's and RE-M5's tests compile and run with
no broker present, and nothing more.

**Deliverable**: Build dependency declaration and broker connection configuration, within the
approved Java 17 / Spring Boot / Maven stack.

**Requirements owned**: RE-O3, RE-R18.

**Non-blocking boundaries resolved here**: RE-D4 (broker connection configuration keys and default
values), RE-D5 (client library choice and version).

**Explicit Exclusions**:

- No publishing behavior, no event type, no event payload, no failure handling, no logging, no
  counting — all of that is RE-M4.
- No change to the creation path — that is RE-M6.
- No tests; this is not a RED milestone.
- No Docker, embedded broker, or real broker for tests (RE-X10).
- No metrics backend, exporter, dashboard, alert, or tracing dependency (RE-X9).
- No dependency serving anything other than the approved Kafka publish (RE-R2, RE-X5).
- No change to the existing operations' external behavior (RE-X8).

**Success Criteria**:

- The application starts and runs with no broker present (RE-O3).
- `mvn test` and `mvn verify` complete successfully with no broker present (RE-R18), and all existing
  tests still pass unchanged.
- No excluded capability from Out of Scope is introduced.
- The change is limited to the prerequisites named above.

---

### RE-M3 — RED: Event publishing behavior — tests

**Type**: RED

**Predecessor**: RE-M2

**Successor**: RE-M4

**Goal**: Establish executable evidence for the required publishing behavior, failing for the single
reason that the behavior does not exist yet.

**Deliverable**: Tests only.

**Behaviors the tests must verify**:

- A publish attempt carries a `ReservationCreated` event that conforms to the approved external
  contract from RE-M1 (RE-R4 payload conformance).
- The event carries `userId` as its only user identification and carries no email address or other
  personal data (RE-R7).
- Publishing is best-effort: a failure at the broker boundary is absorbed rather than propagated
  (RE-R8), is not retried durably (RE-R10), and an unsent event is not preserved for later delivery
  (RE-R10).
- Repeated delivery of the same event is permitted; no exactly-once or de-duplicating behavior is
  asserted (RE-R11).
- When the broker is slow or unavailable, the publish attempt fails without propagating and the
  failure is recorded (RE-R12).
- A failed publish is logged and counted inside the application (RE-R16).

**Constraints**: Publish behavior is verified with an in-process test double only (RE-R17). Tests
must run with no broker present (RE-O3, RE-R18).

**Requirements owned**: RE-R17 (standing testing constraint; it also constrains RE-M5).

**Requirements verified**: RE-R7, RE-R8, RE-R10, RE-R11, RE-R12, RE-R16.

**Explicit Exclusions**:

- No production code, no production scaffolding, no configuration, no dependency change.
- No tests for the creation path, its response, or its stored state — those are RE-M5.
- No test asserting behavior for any event other than `ReservationCreated` (RE-X2, RE-R6).
- No Docker, embedded broker, or real broker (RE-X10).
- No decision about the internal structure used to publish (RE-D1), the counting mechanism (RE-D2),
  or log wording and fields (RE-D3); the tests must assert the required behavior without fixing
  those choices beyond what the behavior requires.
- No change to existing tests' expectations.

**Success Criteria**:

- The new tests fail, and every failure is attributable to the intentionally absent approved
  publishing behavior — including a compilation failure caused solely by an approved production type,
  method, or signature that does not exist yet.
- No failure is caused by an unrelated compilation, configuration, dependency, or environment
  problem, and none is caused by a missing broker.
- All existing tests still pass unchanged.
- Verification is run with the approved commands with no broker present, and its output is recorded
  as the RED evidence.

---

### RE-M4 — GREEN: Event publishing behavior

**Type**: GREEN

**Predecessor**: RE-M3

**Successor**: RE-M5

**Goal**: Deliver the smallest behavior that makes RE-M3's evidence pass.

**Deliverable**: The behavior that publishes a `ReservationCreated` event to Kafka in conformance
with the approved contract, absorbs a publish failure, and logs and counts it.

**Behavior delivered**:

- A `ReservationCreated` event conforming to the approved external contract is published to Kafka
  (RE-R4 payload and publishing behavior), carrying `userId` and no personal data (RE-R7).
- Best-effort semantics: a publish failure is absorbed and not propagated (RE-R8); no durable retry
  and no preservation of unsent events (RE-R10); duplicate delivery is tolerated and no exactly-once
  behavior is introduced (RE-R11).
- A slow or unavailable broker produces a failed publish that is absorbed and recorded (RE-R12).
- A failed publish is logged and counted, with the count internal to the application (RE-R16,
  RE-D2).

**Requirements owned**: RE-R7, RE-R8, RE-R10, RE-R11, RE-R12, RE-R16.

**Non-blocking boundaries resolved here**: RE-D1 (the internal structure used to publish, as far as
this behavior needs it), RE-D2 (the counting mechanism, kept internal to the application), RE-D3
(log level, wording, and field naming).

**Explicit Exclusions**:

- No change to the creation path and no wiring of the publish into it — that is RE-M6.
- No new tests beyond what RE-M3 established; no change to RE-M3's expectations.
- No durable retry, outbox, persistent queue, or dead-letter handling (RE-X3).
- No transactional or exactly-once guarantee (RE-X4).
- No metrics backend, exporter, dashboard, alert, or tracing, and no exposure of the count outside
  the application (RE-X9, RE-D2).
- No event other than `ReservationCreated` (RE-X2).
- No personal data in the event (RE-X6).
- No change to the existing operations' external behavior (RE-X8).
- No capability beyond the approved Kafka publish (RE-R2, RE-X5).

**Success Criteria**:

- All RE-M3 tests pass, and all existing tests still pass unchanged.
- `mvn test` and `mvn verify` complete successfully with no broker present (RE-O3, RE-R18).
- The change is the smallest one satisfying RE-M3's evidence; no unrelated refactoring, dependency,
  or capability is introduced.

---

### RE-M5 — RED: Creation-path integration — tests

**Type**: RED

**Predecessor**: RE-M4

**Successor**: RE-M6

**Goal**: Establish executable evidence that the creation path triggers the publish exactly on
success and that a publish failure cannot affect the creation outcome.

**Deliverable**: Tests only.

**Behaviors the tests must verify**:

- A successful reservation creation results in exactly one `ReservationCreated` publish attempt
  (RE-R4, RE-R5).
- A creation rejected by validation results in no publish attempt (RE-R5).
- A creation rejected by conflict detection results in no publish attempt (RE-R5).
- When the publish fails, the created reservation's success response — its status and its body — is
  unchanged, and the reservation remains created and retrievable in its normal state (RE-R9).
- A failed publish during creation leaves the stored reservation state unchanged from the successful
  case (RE-R9).

**Prerequisites available**: after RE-M2 and RE-M4, the messaging prerequisites and the publishing
behavior both exist, so these tests can substitute an in-process double for the publishing behavior
and run with no broker present. The absence of the trigger in the creation path is the intended RED
condition.

**Constraints**: in-process test double only (RE-R17); tests run with no broker present (RE-O3,
RE-R18).

**Requirements verified**: RE-R4, RE-R5, RE-R9.

**Explicit Exclusions**:

- No production code, no production scaffolding, no configuration, no dependency change.
- No re-verification of the publishing behavior owned by RE-M3/RE-M4.
- No Docker, embedded broker, or real broker (RE-X10).
- No assertion that fixes where in the creation path the publish is triggered from or the internal
  structure used (RE-D1).
- No test asserting behavior for any event other than `ReservationCreated` (RE-X2).
- No change to the existing operations' expected external behavior; existing expectations stand
  unchanged (RE-X8).

**Success Criteria**:

- The new tests fail, and every failure is attributable to the intentionally absent trigger in the
  creation path — including a compilation failure caused solely by an approved production type,
  method, or signature that does not exist yet.
- No failure is caused by an unrelated compilation, configuration, dependency, or environment
  problem, and none is caused by a missing broker.
- All existing tests, including those from RE-M3, still pass unchanged.
- Verification is run with the approved commands with no broker present, and its output is recorded
  as the RED evidence.

---

### RE-M6 — GREEN: Creation-path integration

**Type**: GREEN

**Predecessor**: RE-M5

**Successor**: None (delivery complete; Final Review follows)

**Goal**: Deliver the smallest behavior that makes RE-M5's evidence pass.

**Deliverable**: The behavior that triggers exactly one `ReservationCreated` publish for a
successfully created reservation, and none otherwise, without affecting the creation outcome.

**Behavior delivered**:

- A successful reservation creation publishes one `ReservationCreated` event (RE-R4).
- A publish is attempted only for a reservation that was actually created; a creation rejected by
  validation or by conflict detection publishes nothing (RE-R5).
- A publish failure changes neither the creation response's status, nor its body, nor the stored
  reservation state (RE-R9).

**Requirements owned**: RE-R4, RE-R5, RE-R9.

**Non-blocking boundaries resolved here**: RE-D1 (where the publish is triggered from within the
existing creation path).

**Explicit Exclusions**:

- No change to the publishing behavior delivered by RE-M4 beyond what RE-M5's evidence requires.
- No new tests; no change to RE-M5's expectations.
- No cancellation event or any other event (RE-X2).
- No durable retry, outbox, persistent queue, or dead-letter handling (RE-X3).
- No transactional or exactly-once publish guarantee, and no change to the existing atomic conflict
  check's guarantees (RE-X4, RE-X8).
- No change to the existing operations' external behavior (RE-X8).
- No capability beyond the approved Kafka publish (RE-R2, RE-X5).

**Success Criteria**:

- All RE-M5 tests pass, and all existing tests, including those from RE-M3, still pass unchanged.
- `mvn test` and `mvn verify` complete successfully with no broker present (RE-O3, RE-R18).
- The change is the smallest one satisfying RE-M5's evidence; no unrelated refactoring, dependency,
  or capability is introduced.

---

## Requirement Traceability

Every approved requirement has exactly one owning milestone, or is an exclusion with no owning
milestone that is verified at Final Review.

| Requirement | Owning milestone | Verified by |
|-------------|------------------|-------------|
| RE-R1 (Kafka publish approved, lifting one product-level exclusion) | None — scope authority for this Plan | Final Review |
| RE-R2 (all other product-level exclusions remain in force) | None — exclusion | Final Review |
| RE-R3 (product-level document made out of date; this work item must not edit it) | None — constraint on this work item | Final Review, which lists the out-of-date product-level statements |
| RE-R4 (successful creation publishes one `ReservationCreated` event) | RE-M6 | RE-M5 |
| RE-R5 (publish only for a reservation actually created) | RE-M6 | RE-M5 |
| RE-R6 (`ReservationCreated` is the only event) | None — exclusion | Final Review |
| RE-R7 (`userId` only; no personal data) | RE-M4 | RE-M3 |
| RE-R8 (best-effort delivery) | RE-M4 | RE-M3 |
| RE-R9 (creation response, body, and stored state unchanged on publish failure) | RE-M6 | RE-M5 |
| RE-R10 (no durable retry; unsent events may be lost) | RE-M4 | RE-M3 |
| RE-R11 (duplicates acceptable; exactly-once not required) | RE-M4 | RE-M3 |
| RE-R12 (behavior when broker is slow or unavailable) | RE-M4 | RE-M3 |
| RE-R16 (failed publish logged and counted) | RE-M4 | RE-M3 |
| RE-R17 (in-process test double only) | RE-M3 (standing constraint, also binding on RE-M5) | Human review of RE-M3 and RE-M5 evidence |
| RE-R18 (`mvn test` and `mvn verify` pass with no broker) | RE-M2 | Re-verified by the verification of RE-M2, RE-M3, RE-M4, RE-M5, RE-M6 |
| RE-O1 (single running instance) | None — deployment constraint | Final Review |
| RE-O2 (best-effort; duplicates acceptable; loss on restart; no ordering guarantee required, ordering deferred to RE-C1) | None — realized through RE-R8, RE-R10, RE-R11 (RE-M4) and RE-C1 (RE-M1) | RE-M3 and human review of RE-M1 |
| RE-O3 (application starts and runs, and the build passes, with no broker) | RE-M2 | RE-M2 verification, re-verified by every later milestone |
| RE-O4 (failed publishes logged and counted; nothing else required) | None — realized through RE-R16 (RE-M4) | RE-M3 |
| RE-O5 (scale not required) | None — exclusion | Final Review |
| RE-O6 (availability and latency not required) | None — exclusion | Final Review |
| RE-O7 (security not required beyond RE-R7) | None — exclusion | Final Review |
| RE-C1 (topic, key, serialization, schema and field set, versioning, ordering) | RE-M1 | Human review of the contract artifact |
| RE-X1 – RE-X11 (explicit exclusions) | None — exclusions | Final Review |

### Cross-Cutting Concerns

| Concern | Owning milestone |
|---------|------------------|
| Observability of failed publishes — logging and counting (RE-R16, RE-O4, RE-D2, RE-D3) | RE-M4 |
| Resilience to a slow or unavailable broker, and isolation of publish failure from the creation outcome (RE-R8, RE-R12) | RE-M4 for absorbing and recording the failure; RE-M6 for the creation outcome being unaffected (RE-R9) |
| Build and runtime prerequisites for broker-free startup and verification (RE-O3, RE-R18, RE-D4, RE-D5) | RE-M2 |
| No personal data in the published event (RE-R7) | RE-M4, within the schema approved by RE-M1 |
| Preservation of the existing operations' external behavior (RE-X8) | RE-M6, verified by RE-M5 and by the existing tests continuing to pass in every milestone |
| Testing approach — in-process double only, no broker (RE-R17, RE-R18) | RE-M3, binding on RE-M5 |

## Non-Blocking Boundaries

Recorded in the requirements as non-blocking; each is resolved in the named milestone's
Implementation Plan, not in this Plan.

| Boundary | Resolved in |
|----------|-------------|
| RE-D1 — where the publish is triggered from in the existing creation path, and the internal structure used | RE-M4 (publishing structure) and RE-M6 (trigger point) |
| RE-D2 — the mechanism of the failure count; it stays internal to the application | RE-M4 |
| RE-D3 — log level, message wording, and field naming for a failed publish | RE-M4 |
| RE-D4 — broker connection configuration keys and default values, subject to RE-O3 | RE-M2 |
| RE-D5 — client library choice and version within the approved stack | RE-M2 |

## Plan-Level Success Criteria

- **RE-S1** — The approved external contract resolves every RE-C1 decision and is approved by human
  review (RE-M1).
- **RE-S2** — A successful reservation creation publishes one `ReservationCreated` event that
  conforms to the approved external contract (RE-R4).
- **RE-S3** — A creation rejected by validation or by conflict detection publishes no event (RE-R5).
- **RE-S4** — A creation whose publish fails still returns its normal success response and body, and
  the reservation remains created (RE-R9).
- **RE-S5** — A failed publish is logged and counted inside the application (RE-R16, RE-D2).
- **RE-S6** — The event carries `userId` and no email address or other personal data (RE-R7).
- **RE-S7** — The application starts and runs, and `mvn test` and `mvn verify` complete successfully,
  with no broker present (RE-O3, RE-R18).
- **RE-S8** — Publish behavior is verified with an in-process test double only (RE-R17).
- **RE-S9** — No capability listed in Out of Scope is introduced, and the existing operations'
  external behavior is unchanged (RE-X1 – RE-X11).

## Risks

| ID | Risk | Mitigation within approved scope |
|----|------|----------------------------------|
| RE-K1 | A milestone presumes an RE-C1 decision before RE-M1 is approved. | RE-M1 is the first milestone and owns every RE-C1 decision; no RED or GREEN milestone may fix one. Its Implementation-Plan-free deliverable is reviewed before RE-M2 starts. |
| RE-K2 | Adding a broker client changes application startup or makes the build depend on a broker, breaking RE-O3 or RE-R18. | RE-M2 owns RE-O3 and RE-R18, and its success criteria require startup and both verification commands to succeed with no broker present; every later milestone re-verifies with no broker present. |
| RE-K3 | Publish handling alters the existing creation outcome or the atomic conflict check's guarantees. | RE-R9 is owned by RE-M6 and verified by RE-M5; the existing tests for the creation path must continue to pass unchanged in every milestone. |
| RE-K4 | Failure handling grows into retry, buffering, or exported metrics that the requirements exclude. | RE-X3, RE-X4, and RE-X9 are recorded as exclusions in the affected milestones' Explicit Exclusions; RE-R16 and RE-D2 bound the work to logging and an internal count. |

No mitigation above weakens, reinterprets, or reassigns any requirement or deferred decision.

## Verification

Approved verification commands (`CLAUDE.md`, RE-R18):

```
mvn test
mvn verify
```

Every repository-changing milestone is verified with these commands, run with no broker present
(RE-O3). A milestone is not recorded complete unless both commands completed successfully — except a
RED milestone, whose expected evidence is failing tests attributable only to the intentionally absent
approved behavior, recorded from the same commands.

## Execution Status

| Milestone | Type | Title | Status | Evidence |
|-----------|------|-------|--------|----------|
| RE-M1 | CONTRACT | Reservation event external contract | Complete | `API-Contract.md` approved by human review 2026-09-28; every RE-C1 decision resolved (topic `reservation-events`, key = reservation id, UTF-8 JSON, 8-field schema, in-payload `schemaVersion` 1, no ordering guarantee) |
| RE-M2 | FOUNDATION | Messaging prerequisites for broker-free execution | Complete | `001_Implementation_Plan_RE-M2_FOUNDATION.md` (approved; Revision 1 approved). 2026-09-28, no broker on :9092. `mvn test` and `mvn verify` BUILD SUCCESS, 76/76 passing. `kafka-clients:4.3.1:compile`, no spring-kafka, single `slf4j-api` 2.0.16. `MockProducer` present in the jar. App started via `spring-boot:run` and served HTTP. Only `pom.xml` and `application.properties` changed. Contingency C1 not triggered. |
| RE-M3 | RED | Event publishing behavior — tests | Complete (valid RED) | `002_Implementation_Plan_RE-M3_RED.md` (approved). 2026-09-28, no broker on :9092. Added `ReservationEventPublisherTest.java` (13 tests, identical to the plan). `mvn test` and `mvn verify` fail at `testCompile`: 4 errors, all in that file, all `cannot find symbol: class ReservationEventPublisher` (intentionally absent RE-T1 type). Against the scratch-only stub: 89 run, the 13 new tests fail (2 failures, 11 errors), 76 existing pass. Without the new file: 76/76 BUILD SUCCESS. No production, configuration, or build change. |
| RE-M4 | GREEN | Event publishing behavior | Complete | `003_Implementation_Plan_RE-M4_GREEN.md` (approved, RE-G1–RE-G6). 2026-09-28, no broker on :9092. Created `ReservationEventPublisher` and `ReservationEventsConfiguration`, both identical to the plan. `mvn test` and `mvn verify` BUILD SUCCESS, 89/89; RE-M3 13/13 now GREEN; the RE-G6 flaky test passed in both runs. App started via `spring-boot:run` with the producer (`bootstrap.servers=[localhost:9092]`, `max.block.ms=1000`), HTTP 404 on unknown id, 0 ERROR lines, 10 expected `NetworkClient` WARNs (RE-G5). No existing file, test, or creation-path change. |
| RE-M5 | RED | Creation-path integration — tests | Complete (valid RED) | `004_Implementation_Plan_RE-M5_RED.md` (approved, RE-V1–RE-V3). 2026-09-28, no broker on :9092. Added `ReservationCreationEventPublishingTest.java` (5 tests, identical to the plan). `mvn test` and `mvn verify`: 94 run, `Failures: 5`, exactly the new tests, all assertion failures from the absent creation-path trigger (4 × history `[]` instead of `[id]`, 1 × `failedPublishCount` +0 instead of +1). The other 89 pass; the RE-G6 flaky test passed. Probe in a scratch copy only: 94/94 BUILD SUCCESS, 47.9 s. No production, configuration, build, or existing-test change. |
| RE-M6 | GREEN | Creation-path integration | Complete | `005_Implementation_Plan_RE-M6_GREEN.md` (approved, RE-H1). 2026-09-28, no broker on :9092. `ReservationController.java` changed exactly per the plan (+8/−1): publisher injected; publish runs after `createWithConflictCheck`. `mvn test` BUILD SUCCESS 94/94 in 53.1 s; RE-M5 now 5/5 GREEN; 34 real-client publish-failure WARNs absorbed by the existing HTTP tests. `mvn verify`: 94 run, `Failures: 1`, which was only the RE-G6 flaky `testConcurrentCreatesMixedScenarioCorrectConflictDetection`; recorded, non-blocking per criterion 3; the other 93 passed. Live check: POST returned 201 in 1.15 s with the normal body; one `WARN Failed to publish ReservationCreated … TimeoutException … after 1000 ms`; GET returned 200 with an identical body; 0 ERROR lines. |
| Final Review | — | Final Review of RE-M1–RE-M6 | Complete (awaiting user acceptance) | `Final-Review.md` (2026-09-28). Verdict: approve with findings. RE-S1–RE-S9 and acceptance criteria 1–8 met. 3 product-level statements out of date (RE-P1–RE-P3) and 1 incomplete (RE-P4), for the user to update in `docs/requirements.md`. Findings RE-Q1–RE-Q8: RE-Q1 is a pre-existing flaky test (root cause corrected), RE-Q2 is a pre-existing requirements inconsistency, the rest are accepted, residual, or informational. |

**Overall Plan Status**: Approved (2026-09-28). All milestones RE-M1 through RE-M6 complete (2026-09-28). Final Review written; awaiting user acceptance recorded in `Final-Review.md`.

## Human Review

Status: **Approved** (2026-09-28). Only the user approves this Plan. Completion of any milestone does not authorize
the next; each repository-changing milestone requires its own approved Implementation Plan and human
review.
