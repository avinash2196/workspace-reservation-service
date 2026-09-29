# reservation-events — Final Review

- **Date:** 2026-09-28
- **Scope:** the complete work item, RE-M1 to RE-M6, against the approved `requirements.md`, `Plan.md`,
  and `API-Contract.md`, and Implementation Plans 001–005.
- **Repository:** scratch copy `wrs-trial`, HEAD `a0580d0`. All changes are uncommitted.
- **Authority:** this review produces findings and recommendations only
  (`prompt-driven-development` Final Review Authority). It changes no source, test, or configuration
  file. Every finding that needs an executable change must go through its own Plan, Implementation
  Plan, and RED/GREEN cycle.
- **Labels:**
  - This document introduces `RE-Q` (findings) and `RE-P` (out-of-date product-level statements).
  - Neither collides with the labels used by the referenced artifacts: `RE-R`, `RE-O`, `RE-C`,
    `RE-X`, `RE-D`, `RE-M`, `RE-S`, `RE-K`, `RE-F`, `RE-T`, `RE-G`, `RE-V`, `RE-H`.

## Verdict

**Approve, with findings.** Every Plan-level success criterion (RE-S1 to RE-S9) and every
requirements acceptance criterion (1 to 8) is met, with recorded evidence. No finding is a
correctness defect in the delivered behavior.

- Two findings are pre-existing test and documentation issues that this work surfaced: RE-Q1 and
  RE-Q2.
- The rest are accepted residual risks or informational.
- Three product-level statements are now out of date (RE-P1 to RE-P3), and a fourth is incomplete
  (RE-P4). The user updates `docs/requirements.md`; this review does not edit it (RE-R3).

## Change Set Reviewed

| File | Change | Milestone |
| --- | --- | --- |
| `pom.xml` | +8: `org.apache.kafka:kafka-clients:4.3.1` | RE-M2 |
| `src/main/resources/application.properties` | +3: `reservation-events.kafka.bootstrap-servers`, `…max-block-ms` | RE-M2 |
| `src/test/java/…/event/ReservationEventPublisherTest.java` | new, 235 lines, 13 tests | RE-M3 |
| `src/main/java/…/event/ReservationEventPublisher.java` | new, 78 lines | RE-M4 |
| `src/main/java/…/event/ReservationEventsConfiguration.java` | new, 36 lines | RE-M4 |
| `src/test/java/…/controller/ReservationCreationEventPublishingTest.java` | new, 185 lines, 5 tests | RE-M5 |
| `src/main/java/…/controller/ReservationController.java` | +8/−1: publisher injected; publish after `createWithConflictCheck` | RE-M6 |

Each file matched its Implementation Plan's Exact Code at execution. This was checked with `diff`
or `git apply`, and recorded in `Plan.md`. No file outside these was changed. `git status` shows
only these files and `docs/.ai/reservation-events/`.

## Final Acceptance Criteria and Evidence

Plan-level success criteria (`Plan.md` § Plan-Level Success Criteria):

| Criterion | Met | Evidence |
| --- | --- | --- |
| RE-S1: contract resolves every RE-C1 decision; approved | ✅ | `API-Contract.md` Decisions Resolved (6 rows); approved 2026-09-28 (RE-M1) |
| RE-S2: a successful create publishes one conforming event | ✅ | Creation path: RE-M5 test 1 (history keyed by the created id), GREEN in RE-M6. Conformance: RE-M3 tests 1–7 (topic, key, exact 8 fields, envelope, values, timestamp format, `occurredAt`), GREEN in RE-M4 |
| RE-S3: 400/409 publish nothing | ✅ | RE-M5 tests 2–4 (request validation 400, time-range 400, conflict 409), GREEN in RE-M6 |
| RE-S4: publish failure still returns 201 + body; reservation stays created | ✅ | RE-M5 test 5, GREEN. **End to end with the real client**: RE-M6 V3 POST returned 201 in 1.15 s, GET returned 200 with an identical body, and the log had one `WARN Failed to publish … TimeoutException … after 1000 ms`. The unchanged existing HTTP tests absorbed 34 real publish failures and still passed |
| RE-S5: a failed publish is logged and counted | ✅ | RE-M3 tests 8–10 and 13 (WARN line with the id; `failedPublishCount` for both the send-time and reported-later failure paths) |
| RE-S6: `userId` only, no personal data | ✅ | RE-M3 test 3 (exact field set). The publisher logs only the reservation id and the exception, never `userId` (source inspection) |
| RE-S7: starts, and build passes, with no broker | ✅ | Port 9092 closed in every run (V0). App started in RE-M2, RE-M4, and RE-M6. RE-M6 `mvn test` gave 94/94 BUILD SUCCESS. RE-M6 `mvn verify` gave 93/94, the only failure being the RE-G6 pre-existing flaky test (RE-Q1), which is non-blocking by approved criterion |
| RE-S8: in-process double only | ✅ | `MockProducer` only (RE-F2). No Docker, embedded broker, or `spring-kafka-test` in `pom.xml` |
| RE-S9: no excluded capability; existing HTTP behavior unchanged | ✅ | See Exclusions Check. The 35 existing controller tests pass unchanged |

Requirements acceptance criteria (`requirements.md` § Acceptance Criteria):

| # | Criterion | Met | Evidence |
| --- | --- | --- | --- |
| 1 | A successful POST publishes one event matching the contract | ✅ | RE-S2 |
| 2 | A 400 or 409 create publishes no event | ✅ | RE-S3 |
| 3 | A publish failure still returns 201 and body; the reservation remains | ✅ | RE-S4 |
| 4 | A failed publish is logged and counted | ✅ | RE-S5 |
| 5 | `userId` and no personal data | ✅ | RE-S6 |
| 6 | Starts, and `mvn test`/`mvn verify` pass, with no broker | ✅ | RE-S7 (subject to RE-Q1, pre-existing) |
| 7 | In-process test double only | ✅ | RE-S8 |
| 8 | No excluded capability | ✅ | Exclusions Check |

### Exclusions Check (RE-X1 to RE-X11)

| Exclusion | Held | Evidence |
| --- | --- | --- |
| RE-X1: no email or notification delivery | ✅ | No such code |
| RE-X2: no event other than `ReservationCreated`; no cancel event | ✅ | `DELETE` path untouched; the publisher has one method |
| RE-X3: no durable retry, outbox, or dead letter | ✅ | RE-M3 tests 11–12 (no redelivery); no queue or storage code |
| RE-X4: no transactions or exactly-once | ✅ | No `transactional.id` or `initTransactions`. Client-default idempotence is per-producer-session only and is not an exactly-once guarantee |
| RE-X5: no other integration, persistence, or auth | ✅ | The only new dependency is `kafka-clients` |
| RE-X6: no personal data in the event | ✅ | RE-S6 |
| RE-X7: no scale, availability, latency, or security work | ✅ | None added |
| RE-X8: existing HTTP contract unchanged | ✅ | Existing HTTP tests pass unchanged; the response body is identical (RE-M5 test 5) |
| RE-X9: no metrics backend or exporter | ✅ | The counter is internal (`failedPublishCount()`); no Actuator or Micrometer |
| RE-X10: no Docker or embedded broker in tests | ✅ | RE-S8 |
| RE-X11: no multi-instance behavior | ✅ | None added |

## Product-Level Statements Now Out of Date

This work item changed behavior that `docs/requirements.md` describes. The user updates that file
(RE-R3); this review does not edit it.

| ID | Location in `docs/requirements.md` | Current statement | Why it is out of date | Suggested update (for the user) |
| --- | --- | --- | --- | --- |
| RE-P1 | Line 5, § Application Overview | "…maintains reservation state in application memory only, with no persistence or external integrations." | The service now publishes `ReservationCreated` events to Kafka, an external integration (RE-R1, RE-R3). Reservation state is still in memory only, so that half stays true. | "…maintains reservation state in application memory only, with no persistence. Its only external integration is best-effort publishing of `ReservationCreated` events to Kafka (see `docs/.ai/reservation-events/`)." |
| RE-P2 | Line 23, § Scope Exclusions | "Message queue or event publishing" | Lifted for `ReservationCreated` events to Kafka only (RE-R1) | Qualify it: "Message queue or event publishing, except best-effort `ReservationCreated` publishing to Kafka (reservation-events)". |
| RE-P3 | Line 25, § Scope Exclusions | "External service integrations" | Remains excluded except for the single Kafka publish (RE-R2) | Qualify it: "External service integrations, other than the Kafka publish above". |
| RE-P4 | Lines 46–82, § 1. Create Reservation | Response and behavior list, with no mention of a side effect | *Incomplete rather than contradicted.* A successful create now also publishes one best-effort `ReservationCreated` event, and with no broker a create can take up to about 1 s (`max.block.ms`). The HTTP contract itself is unchanged (RE-X8). | Optionally add: "On 201, a `ReservationCreated` event is published best-effort (contract: `docs/.ai/reservation-events/API-Contract.md`). Publish failure does not affect the response." |

Checked and **still accurate**:

- § Technology Stack (lines 7–14). It does not claim to list every library.
- § Reservation Model.
- § 2 Get and § 3 Cancel. Cancel publishes nothing (RE-R6).
- § Constraints 1–6. In particular, constraint 6 ("state is lost on restart") is consistent with
  RE-R10, since unsent events may be lost.
- § Non-Blocking Implementation Details.
- § Approval Boundaries.

## Findings

| ID | Severity | Origin | Finding | Evidence | Recommendation (human decision; not applied) |
| --- | --- | --- | --- | --- | --- |
| RE-Q1 | Medium (test reliability) | **Pre-existing**, not introduced by this work item | `ReservationStoreConflictTest#testConcurrentCreatesMixedScenarioCorrectConflictDetection` is order-dependent. T2 (10:30–11:30) overlaps **both** T1 (10:00–11:00) and T3 (11:00–12:00). When T2 acquires the synchronized `createWithConflictCheck` first, T1 and T3 are both correctly rejected, giving 2 successes where the test asserts 3. **Correction:** Implementation Plan 003 (Risk 6, RE-G6) attributed this to inclusive end times. That was wrong. The store and `docs/requirements.md` line 68 use half-open overlap (`s1 < e2 && s2 < e1`), so adjacent reservations do not conflict. The flake is caused solely by T2 winning the race. | 12 failures in 30 isolated runs on the RE-M2 baseline, before any event code existed. It failed once in the RE-M6 `mvn verify` run (the only failure out of 94). `ReservationStore.java:85-89` | Rewrite the test to assert interleaving-independent invariants, as a separate test-only change in the source repository. A task has been queued for `workspace-reservation-service`. Production behavior is correct and needs no change. |
| RE-Q2 | Low (documentation) | **Pre-existing** | `docs/requirements.md` line 41 describes `endTime` as "Inclusive end of the reservation", which contradicts the line 68 overlap definition (adjacent reservations do not conflict, i.e. a half-open range). The code follows line 68. | `docs/requirements.md:41` vs `:68`; `ReservationStore.java:76` | The user clarifies line 41, e.g. "Exclusive end" or "end instant; see Overlap Definition". |
| RE-Q3 | Low (operational, accepted) | This work item | With no broker, every successful create blocks its request thread for up to `max.block.ms` = 1000 ms before the publish fails and is absorbed. Under sustained load with the broker down, this ties up servlet threads for about 1 s per create. | RE-M6 V3: 201 in 1.15 s. Test-suite time rose from about 28 s to about 53 s. | Accepted by RE-O5/RE-O6 (no scale or latency target) and RE-F4. If a latency target is set later, revisit RE-F4 or move the publish off the request thread (new Plan). |
| RE-Q4 | Info (accepted) | This work item | With no broker, the Kafka client's I/O thread logs `NetworkClient` connection `WARN`s continuously (about 4 per second idle), in the app and in tests. | RE-M4 dry run and V3; RE-G5 accepted the defaults | None required. If it becomes noisy in operations, tune `reconnect.backoff.max.ms` or the logger level (new Plan). |
| RE-Q5 | Low (residual, pre-existing) | **Pre-existing** | Spring Boot 3.3.3 is past OSS support (ended 2025-06-30) and commercial support (ended 2026-06-30). `kafka-clients` is pinned to the supported 4.3.1, overriding the BOM's managed 3.7.x (RE-F1). | `api.spring.io` generations, cited in 001 | Plan a Spring Boot upgrade as its own work item. After it, re-check whether the explicit `kafka-clients` version is still needed. |
| RE-Q6 | Low (pre-existing) | **Pre-existing** | The build does not produce an executable jar: `spring-boot-maven-plugin` has no `repackage` execution, because the project imports Boot as a BOM with no starter parent. | RE-M2 V5: `no main manifest attribute` (001 Revision 1) | A separate build change if a runnable jar is needed for deployment. |
| RE-Q7 | Info (pre-existing) | **Pre-existing** | `pom.xml` declares `java.version`/`maven.compiler.*` as 21, while `maven-compiler-plugin` pins source and target 17 (the approved stack). | `pom.xml:17-19,70-73` | Align the properties to 17 in a separate cleanup. |
| RE-Q8 | Info (documentation, this work item) | This work item | `requirements.md` (Repository-Confirmed Current State) cites `ReservationController.java:109` for `ISO_INSTANT` formatting. The formatting is at lines 111–115 (`:109` is the method signature), and the line numbers have since shifted by RE-M6. | Source inspection | The user may correct the citation. It does not affect behavior. |

**Code review notes (no finding):**

- **Failures never propagate.** The publisher catches `RuntimeException` from record building and
  `send`, including `InterruptException`, which leaves the thread's interrupt flag set as Kafka
  intends. The callback path records reported-later failures.
- **The counter is thread-safe.** It is an `AtomicLong`, updated from both the request thread and
  the producer I/O thread.
- **The publish is not inside the store lock.** It is outside `ReservationStore`'s `synchronized`
  method, so conflict-check atomicity is unchanged (RE-H1).
- **The event carries the reservation's actual values.** It is built from the stored reservation,
  after conflict detection succeeds.
- **JSON is correct.** It is built with Jackson `ObjectNode`, so `workspaceId`/`userId` are escaped
  properly. The key is `UUID.toString()`, the canonical lowercase form. `StringSerializer` defaults
  to UTF-8, as the contract requires.
- **Producer shutdown is bounded.** The producer is closed with the Spring context (inferred
  `close()`). With no broker, no records are ever queued, so shutdown is immediate. With a slow
  broker, it is bounded by `delivery.timeout.ms` (003 Risk 3).

## Recommendation

1. **Accept** the delivered work item. Every approved acceptance criterion is met.
2. **User action:** update `docs/requirements.md` for RE-P1 to RE-P3 (and optionally RE-P4), and
   clarify RE-Q2.
3. **Separate work, not part of `reservation-events`:**
   - RE-Q1: fix the flaky test (a task has been queued);
   - RE-Q5: Spring Boot upgrade;
   - RE-Q6: executable jar;
   - RE-Q7: pom property alignment.
4. **Commit:** nothing has been committed in the scratch copy. Commit only on the user's instruction.

## User Decision

- Findings accepted: _(to be recorded by the user)_
- Product-level requirements updated: _(to be recorded by the user)_
