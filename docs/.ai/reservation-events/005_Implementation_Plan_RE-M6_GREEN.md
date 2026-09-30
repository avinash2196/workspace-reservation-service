# 005 — Implementation Plan — RE-M6 GREEN: Creation-path integration

## Milestone

- **Work item:** `reservation-events`
- **Milestone:** RE-M6, Creation-path integration
- **Milestone type (as recorded in `Plan.md`):** GREEN
- **Predecessor:** RE-M5 (RED)
- **Successor:** none; Final Review follows
- **Status:** Approved 2026-09-28 (see Human Review). **Executed and verified 2026-09-28**, with all
  criteria met. The RE-G6 flaky test failed once in `mvn verify` and is recorded as non-blocking.
  See the `Plan.md` Execution Status entry for the evidence.

## Authoritative References

| Artifact | Status |
| --- | --- |
| `docs/.ai/reservation-events/requirements.md` | Approved 2026-09-28 |
| `docs/.ai/reservation-events/Plan.md` (§ RE-M6) | Approved 2026-09-28 |
| `docs/.ai/reservation-events/API-Contract.md` | Approved 2026-09-28 |
| `001`–`004` Implementation Plans | Approved and executed |
| `docs/.ai/initial-build/0_API_Contract.md` | Existing HTTP contract; must stay unchanged (RE-X8) |

## Predecessor RED Evidence

`Plan.md` records **RE-M5 Complete (valid RED)** on 2026-09-28, with no broker present.

- **Repository run:** `mvn test` and `mvn verify` ran 94 tests with `Failures: 5`, exactly the new
  `ReservationCreationEventPublishingTest` tests. All 5 were assertion failures caused by the absent
  creation-path trigger:
  - four tests found history `[]` where `[id]` was expected;
  - one test found `failedPublishCount` +0 where +1 was expected.
- **Other tests:** the remaining 89 passed.
- **Scratch-only probe:** with the probe applied, 94/94 passed.

## Current Repository State (inspected 2026-09-28)

- `git status --short` lists:
  - `M pom.xml`
  - `M src/main/resources/application.properties`
  - `?? docs/.ai/reservation-events/`
  - `?? src/main/java/com/example/workspace/reservation/event/`
  - `?? src/test/java/com/example/workspace/reservation/controller/ReservationCreationEventPublishingTest.java`
  - `?? src/test/java/com/example/workspace/reservation/event/`
- `ReservationController` (`ReservationController.java:20-31`) has constructor injection of
  `ReservationValidator` and `ReservationStore` via `@Autowired`. `createReservation` (lines 33–67)
  stores the reservation with `store.createWithConflictCheck(reservation)` at line 63, then returns
  201. It calls no publisher.
- Nothing constructs `ReservationController` directly: there is no `new ReservationController(` in
  `src/`. Adding a constructor parameter therefore affects only Spring wiring.
- `ReservationStore.createWithConflictCheck` is `synchronized` and holds the atomic
  check-and-insert. It throws `ConflictException` on overlap, so reaching the next line means the
  reservation was actually created (RE-R5).
- The `ReservationEventPublisher` bean exists (RE-M4, RE-G1). Its `publishReservationCreated` never
  throws on publish failure (RE-T1) and does not wait for acknowledgment (RE-T3).

## Carried-Forward Decisions

| Decision | Approved in | How this plan honors it |
| --- | --- | --- |
| RE-T1: publisher surface; never throws on publish failure | 002 | The controller calls it directly, with no `try/catch` (RE-V1 rationale) |
| RE-T3: no waiting for acknowledgment | 002 | The response is not delayed by broker acknowledgment |
| RE-F4: `max.block.ms=1000` | 001 | Bounds the synchronous part of a publish with no broker to 1 s |
| RE-G1: producer and publisher beans | 003 | The controller receives the existing publisher bean; bean shape unchanged (RE-M5 Risk 3) |
| RE-G6: the known flaky `ReservationStoreConflictTest#testConcurrentCreatesMixedScenarioCorrectConflictDetection` is recorded and does not block | 003 | Carried into the criteria |
| RE-V1: broker-boundary double in the RE-M5 tests | 004 | Unchanged |
| RE-V3: existing HTTP tests unchanged; they exercise the real producer with no broker | 004 | Unchanged; the slowdown is re-measured in V1 |

## Decisions for Review

The label `RE-H1` is introduced here. It does not collide with `RE-R`, `RE-O`, `RE-C`, `RE-X`,
`RE-D`, `RE-M`, `RE-S`, `RE-K`, `RE-F`, `RE-T`, `RE-G`, or `RE-V`.

| ID | Decision | Proposed | Alternatives | Why |
| --- | --- | --- | --- | --- |
| RE-H1 | Trigger point, i.e. the RE-D1 part left to RE-M6 | In `ReservationController.createReservation`, immediately after `store.createWithConflictCheck(reservation)` returns and before the 201 is built. The call is outside the store's `synchronized` method. The publisher is injected through the existing constructor. | (a) Inside `ReservationStore.createWithConflictCheck`. That would hold the store lock during a publish that can block for up to 1 s, and couple the store to messaging. (b) A Spring application event with a listener. That adds an abstraction no requirement asks for. (c) Field injection. That is inconsistent with the controller's existing constructor injection. | This is the only point where creation is known to have succeeded (RE-R5). The atomic conflict check's guarantees stay unchanged (RE-X4, RE-X8). It is the smallest change. |

## Files in Scope

| File | Action |
| --- | --- |
| `src/main/java/com/example/workspace/reservation/controller/ReservationController.java` | Modify (diff below) |

No other file changes. `Plan.md` Execution Status is updated after verified execution.

## Ordered Proposed Changes

1. Add `import com.example.workspace.reservation.event.ReservationEventPublisher;`.
2. Add the field `private final ReservationEventPublisher eventPublisher;`.
3. Extend the `@Autowired` constructor with a `ReservationEventPublisher eventPublisher` parameter and
   assign the field.
4. In `createReservation`, after
   `Reservation storedReservation = store.createWithConflictCheck(reservation);`, add a comment line
   and `eventPublisher.publishReservationCreated(storedReservation);` (RE-H1).

## Exact Code

### `src/main/java/com/example/workspace/reservation/controller/ReservationController.java` (complete unified diff)

```diff
--- a/src/main/java/com/example/workspace/reservation/controller/ReservationController.java
+++ b/src/main/java/com/example/workspace/reservation/controller/ReservationController.java
@@ -2,6 +2,7 @@ package com.example.workspace.reservation.controller;
 
 import com.example.workspace.reservation.dto.CreateReservationRequest;
 import com.example.workspace.reservation.dto.ReservationResponse;
+import com.example.workspace.reservation.event.ReservationEventPublisher;
 import com.example.workspace.reservation.exception.ResourceNotFoundException;
 import com.example.workspace.reservation.service.ReservationValidator;
 import com.example.workspace.reservation.store.ReservationStore;
@@ -23,11 +24,14 @@ public class ReservationController {
 
     private final ReservationValidator validator;
     private final ReservationStore store;
+    private final ReservationEventPublisher eventPublisher;
 
     @Autowired
-    public ReservationController(ReservationValidator validator, ReservationStore store) {
+    public ReservationController(ReservationValidator validator, ReservationStore store,
+                                 ReservationEventPublisher eventPublisher) {
         this.validator = validator;
         this.store = store;
+        this.eventPublisher = eventPublisher;
     }
 
     @PostMapping
@@ -62,6 +66,9 @@ public class ReservationController {
         // Store reservation with conflict detection
         Reservation storedReservation = store.createWithConflictCheck(reservation);
 
+        // Best-effort ReservationCreated event; publish failures are absorbed by the publisher
+        eventPublisher.publishReservationCreated(storedReservation);
+
         // Return 201 Created with response body
         return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(storedReservation));
     }
```

## Acceptance / Completion Criteria

RE-M6 is complete when all of the following are true:

1. `ReservationController.java` is changed exactly as in the diff, and no other file changes
   (excluding the `Plan.md` Execution Status update).
2. All 5 RE-M5 tests pass without modification.
3. All other 89 tests pass without modification (94 in total), subject to RE-G6: a run whose only
   failure is the named flaky test does not block, and it is recorded.
4. `mvn test` and `mvn verify` complete successfully with no broker present (RE-O3, RE-R18).
5. With the application running and no broker present, `POST /api/v1/reservations` with a valid body
   returns 201 with the normal body. The log shows one `WARN` line,
   `Failed to publish ReservationCreated for reservation <id>`. A `GET` of that id returns 200
   (RE-R9, RE-R12, end to end with the real client).
6. `ReservationStore`, `ReservationEventPublisher`, `ReservationEventsConfiguration`, and all tests
   are unchanged. The existing HTTP contract is unchanged (RE-X8), as shown by the unchanged existing
   HTTP tests passing.
7. No excluded capability is introduced:
   - no other event, and no cancellation trigger (RE-X2);
   - no retry, outbox, transaction, or exactly-once behavior (RE-X3, RE-X4);
   - no new dependency or configuration.

## Verification Commands

`<repo>` is the repository root. All results are **Expected / Predicted — Not Yet Verified**.

| Step | Command (bash) | Expected |
| --- | --- | --- |
| V0 | `powershell -Command "(Test-NetConnection localhost -Port 9092 -WarningAction SilentlyContinue).TcpTestSucceeded"` | `False` |
| V1 | `cd <repo> && mvn -B test` | BUILD SUCCESS. `Tests run: 94, Failures: 0, Errors: 0`, including RE-M5 5/5. `Total time` is recorded as the RE-V3 cost. |
| V2 | `cd <repo> && mvn -B verify` | BUILD SUCCESS, 94/94 |
| V3 | Start with `mvn -B spring-boot:run "-Dspring-boot.run.arguments=--server.port=0"`. Once started, send `POST /api/v1/reservations` with `{"workspaceId":"ws-v3","userId":"user-v3","startTime":"2026-10-02T09:00:00Z","endTime":"2026-10-02T10:00:00Z"}`, then `GET /api/v1/reservations/<id>`, then stop the forked JVM by its logged PID. | POST returns `201` in about 1 s or more (bounded by `max.block.ms`), with a body carrying the 6 fields and status `ACTIVE`. GET returns `200`. The log contains exactly one `WARN … Failed to publish ReservationCreated for reservation <id>` and no `ERROR` line. |
| V4 | `cd <repo> && git status --short && git diff --stat` | As in Current Repository State, plus `M src/main/java/com/example/workspace/reservation/controller/ReservationController.java`. The tracked diff covers `pom.xml`, `application.properties`, and `ReservationController.java` only. |
| V5 | `git diff` of `ReservationController.java` compared with the Exact Code diff | Identical hunks |

**Repeatability and isolation:** no new test is added, and no test overrides configuration. The
existing isolation is unchanged: the RE-M5 per-test reset, and `@DirtiesContext` in
`ReservationControllerHttpAPITest`. V3 uses `--server.port=0`.

**Planning-time dry run (2026-09-28).** This is not GREEN evidence; RE-M6 has not been executed. The
diff above is the `git diff` taken from a scratch copy outside the repository. In that copy:

- `mvn -B test` reported `Tests run: 94, Failures: 0, Errors: 0` and BUILD SUCCESS in 42.4 s;
- RE-M5 passed 5/5;
- the existing HTTP tests produced 34 `Failed to publish ReservationCreated` `WARN` lines from the
  real `KafkaProducer` with no broker, and all of them still passed.

## Pre-authorized Contingencies

None. Any failure stops execution and returns to planning.

## Rollback / Recovery

Revert only this file with `git restore
src/main/java/com/example/workspace/reservation/controller/ReservationController.java`. Do not use
any repository-wide reset, clean, or checkout.

## Risks

1. **Create latency with no broker.** Each successful create waits up to 1 s (`max.block.ms`,
   RE-F4) for metadata before the publish fails and is absorbed. This is accepted by RE-O6 (no
   latency target) and RE-R12. With a healthy broker, the send hands off without waiting for
   acknowledgment (RE-T3).
2. **Event published before the response is written.** If writing the 201 response then fails, for
   example because the client disconnected, the event still exists for a reservation that does exist.
   This is consistent with RE-R4 (the reservation was created).
3. **Test-suite duration** rises by about 14–20 s (RE-V3, measured).

## Explicit Exclusions

- No change to `ReservationStore` or its `synchronized` conflict check (RE-X4, RE-X8).
- No change to the publisher, its configuration, or any test.
- No `try/catch` around the publish. The publisher already absorbs failures (RE-T1).
- No event on cancellation or any other operation (RE-X2).
- No retry, outbox, or dead letter (RE-X3). No metrics exposure (RE-X9).
- No change to the existing HTTP contract (RE-X8).
- No edit to `docs/requirements.md` (RE-R3). Out-of-date statements are listed at Final Review.

## Human Review

- Status: **Approved** as written by human review (2026-09-28), including RE-H1. Approval authorizes
  RE-M6 execution only. Final Review follows separately.
