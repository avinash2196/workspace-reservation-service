# 004 — Implementation Plan — RE-M5 RED: Creation-path integration — tests

## Milestone

- **Work item:** `reservation-events`
- **Milestone:** RE-M5, Creation-path integration — tests
- **Milestone type (as recorded in `Plan.md`):** RED
- **Successor:** RE-M6 (GREEN)
- **Status:** Approved 2026-09-28 (see Human Review). **Executed 2026-09-28: valid RED established**,
  with all criteria met. See the `Plan.md` Execution Status entry for the evidence.

## Authoritative References

| Artifact | Status |
| --- | --- |
| `docs/.ai/reservation-events/requirements.md` | Approved 2026-09-28 |
| `docs/.ai/reservation-events/Plan.md` (§ RE-M5) | Approved 2026-09-28 |
| `docs/.ai/reservation-events/API-Contract.md` | Approved 2026-09-28 |
| `001_Implementation_Plan_RE-M2_FOUNDATION.md` | Approved, executed |
| `002_Implementation_Plan_RE-M3_RED.md` | Approved, executed (valid RED) |
| `003_Implementation_Plan_RE-M4_GREEN.md` | Approved, executed, verified |
| `docs/.ai/initial-build/0_API_Contract.md` | Existing HTTP contract, unchanged by this work item (RE-X8) |

## Predecessor Evidence

`Plan.md` records **RE-M4 Complete** on 2026-09-28:

- no broker was present;
- `mvn test` and `mvn verify` gave BUILD SUCCESS with 89/89, and RE-M3 was 13/13 GREEN;
- the app started with the producer bean.

RE-M1 through RE-M3 are also Complete.

## Current Repository State (inspected 2026-09-28)

- **`git status --short`:** only the RE-M2, RE-M3, and RE-M4 changes plus `docs/.ai/reservation-events/`.
- **Creation path:** `ReservationController.createReservation` (`ReservationController.java:33-67`)
  runs these steps in order:
  - `validator.validateCreateReservationRequest` → `ValidationException` → 400;
  - `Instant.parse`;
  - the start-before-end check → `ValidationException` → 400;
  - `Reservation.create`;
  - `store.createWithConflictCheck` → `ConflictException` → 409;
  - 201 with `toResponse(...)`.

  It calls no publisher. That absent trigger is the intended RED condition.
- **Beans (RE-M4):** `ReservationEventsConfiguration` defines `Producer<String, String>
  reservationEventsProducer` (a real `KafkaProducer`) and `ReservationEventPublisher
  reservationEventPublisher(Producer<String, String>)`. `failedPublishCount()` is a cumulative
  per-bean counter.
- **Existing HTTP tests:**
  - `ReservationControllerHttpAPITest` uses `@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureMockMvc`,
    and `@DirtiesContext(AFTER_EACH_TEST_METHOD)`. It has 28 `perform(post…)` call sites, including
    3 two-thread race tests with `endSignal.await(10, SECONDS)`.
  - `ReservationControllerValidationTest` has 9 `perform(post…)` call sites.
  - Both use the real producer bean.
- **Response body of a created reservation** (`initial-build` contract, lines 36 and 114):
  `id`, `workspaceId`, `userId`, `startTime`, `endTime`, `status` (`ACTIVE`).
- **`MockProducer` 4.3.1:**
  - `clear()` empties the history and pending completions but does not reset `sendException`;
  - with `autoComplete=true`, callbacks run immediately on the sending thread.

## Carried-Forward Decisions

| Decision | Approved in | Use here |
| --- | --- | --- |
| RE-F2: `MockProducer` is the in-process broker double (RE-R17) | 001 | The broker double for these tests |
| RE-T1: `ReservationEventPublisher` surface; `publishReservationCreated` never throws on publish failure | 002 | The real publisher runs in the test context. Its `failedPublishCount()` shows that a failed publish was attempted. |
| RE-T3: publish does not wait for acknowledgment | 002 | A failure reported later arrives after the HTTP response and cannot affect it |
| RE-G1: producer and publisher are Spring beans | 003 | The test overrides the producer with a `@Primary` `MockProducer` bean |
| RE-G6: the known flaky `ReservationStoreConflictTest#testConcurrentCreatesMixedScenarioCorrectConflictDetection` is recorded and does not block | 003 | Carried into this plan's criteria |

## Decisions for Review

The labels `RE-V1`–`RE-V3` are introduced here. They do not collide with `RE-R`, `RE-O`, `RE-C`,
`RE-X`, `RE-D`, `RE-M`, `RE-S`, `RE-K`, `RE-F`, `RE-T`, or `RE-G`.

| ID | Decision | Proposed | Alternatives | Why |
| --- | --- | --- | --- | --- |
| RE-V1 | What is doubled on the creation path | The **broker boundary**. A nested `@TestConfiguration` registers a `@Primary MockProducer<String, String>` (`autoComplete=true`) that the real `ReservationEventPublisher` bean receives. A publish is observed through `history()`. A failed publish is produced with `sendException` and observed as a +1 change in `failedPublishCount()`. | `@MockBean ReservationEventPublisher` with Mockito. A failure would then have to be simulated by making the publisher *throw*, which RE-T1 says it never does. RE-M6 would then need a redundant `try/catch` in the controller just to pass. | Simulates the failure where it actually happens. It asserts the observable outcome (an event reached the broker boundary) rather than an internal method call, so it does not fix where in the creation path the trigger lives (RE-D1). |
| RE-V2 | Making the negative cases (400/409 → no publish) fail in RED for the right reason | Each negative test first performs a **successful** create and asserts exactly one published record. It then performs the rejected request and asserts that the history still holds only that one record. | Stand-alone "no publish on 400/409" tests. They would pass vacuously in RED because nothing publishes yet, which is not valid RED evidence. | Every new test fails in RED only because the trigger is absent, and after GREEN each negative test still proves that no extra event is published. |
| RE-V3 | Existing HTTP tests once RE-M6 wires the real producer in | **Leave them unchanged.** With no broker, each successful create there will block for up to `max.block.ms` (1 s, RE-F4) before the publish fails and is absorbed. At most about 37 POST sites, not all successful, gives an estimated ≤ ~30 s extra per run. The 10 s latches in the race tests are unaffected, because each thread blocks for at most 1 s. As a side effect, these tests become end-to-end evidence with the **real** client that a failed publish changes no HTTP outcome (RE-R9, RE-R12). | (a) A test-only `src/test/resources/config/application.properties` lowering `reservation-events.kafka.max-block-ms`. This adds a configuration file and hides the real timing. (b) Add the broker double to the existing test classes. That changes existing test files, which the Plan asks to stand unchanged. | Smallest change. No existing test file changes. |

## Files in Scope

| File | Action |
| --- | --- |
| `src/test/java/com/example/workspace/reservation/controller/ReservationCreationEventPublishingTest.java` | Create |

No production, configuration, build, or existing test file changes. `Plan.md` Execution Status is
updated after verified execution.

## Ordered Proposed Changes

1. Create `ReservationCreationEventPublishingTest.java` with the nested `BrokerDoubleConfiguration`
   (RE-V1) and exactly the 5 tests below.

## Test → Requirement Mapping

| # | Test method | Verifies | RED failure (Expected / Predicted) |
| --- | --- | --- | --- |
| 1 | `successfulCreatePublishesOneEventForTheCreatedReservation` | RE-R4: a 201 create yields exactly one published record, keyed by the created reservation's `id` (ties the event to the response). | `history()` has size 0, expected 1 |
| 2 | `createRejectedByRequestValidationPublishesNothing` | RE-R5 (400 from request validation: missing `workspaceId`) | The precondition publish is missing: size 0, expected 1 |
| 3 | `createRejectedByTimeRangeValidationPublishesNothing` | RE-R5 (400 from the controller's `startTime < endTime` check) | Same |
| 4 | `createRejectedByConflictPublishesNothing` | RE-R5 (409 from the store's conflict detection) | Same |
| 5 | `publishFailureDoesNotChangeCreationResponseOrStoredReservation` | RE-R9: with the broker failing at `send`, the create still returns 201 with the normal body (the exact 6 fields, requested values, `status` `ACTIVE`). The publish was attempted and failed (+1 `failedPublishCount`). `GET` returns an identical body. An overlapping create still gets 409, so the reservation holds its slot in conflict detection. | `failedPublishCount` +0, expected +1 |

Not tested, with reasons:

- **A failure reported after `send` (the asynchronous callback path).** Under RE-T3 it arrives only
  after the HTTP response has been written. It touches nothing but the publisher's counter, which
  RE-M3 already covers.
- **Event payload conformance** is already verified by RE-M3. The Plan excludes re-verifying it here.
- **Cancellation publishes nothing** (RE-R6). No test asserts non-behavior of an unrelated operation,
  and RE-M6 does not touch `DELETE`.

## Exact Code

### `src/test/java/com/example/workspace/reservation/controller/ReservationCreationEventPublishingTest.java` (new, complete content)

```java
package com.example.workspace.reservation.controller;

import com.example.workspace.reservation.event.ReservationEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RED tests for reservation-events RE-M5: the creation path publishes ReservationCreated exactly once
 * per successful create, never for a rejected create, and a failed publish changes nothing about the
 * creation outcome. The broker boundary is doubled in process with Kafka's MockProducer (RE-R17).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("ReservationController - ReservationCreated publishing on the creation path")
class ReservationCreationEventPublishingTest {

    private static final String BASE_URL = "/api/v1/reservations";

    @TestConfiguration
    static class BrokerDoubleConfiguration {

        @Bean
        @Primary
        MockProducer<String, String> brokerDouble() {
            return new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockProducer<String, String> brokerDouble;

    @Autowired
    private ReservationEventPublisher publisher;

    private String workspaceId;

    @BeforeEach
    void setUp() {
        brokerDouble.clear();
        brokerDouble.sendException = null;
        workspaceId = "ws-" + UUID.randomUUID();
    }

    private static String requestBody(String workspaceId, String userId, String startTime, String endTime) {
        return String.format("""
            {
                "workspaceId": "%s",
                "userId": "%s",
                "startTime": "%s",
                "endTime": "%s"
            }
            """, workspaceId, userId, startTime, endTime);
    }

    private MvcResult create(String body, int expectedStatus) throws Exception {
        return mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createAndAssertOnePublished() throws Exception {
        JsonNode created = json(create(
                requestBody(workspaceId, "user-1", "2026-10-02T09:00:00Z", "2026-10-02T10:00:00Z"), 201));
        String id = created.get("id").asText();
        assertThat(brokerDouble.history())
                .extracting(ProducerRecord::key)
                .containsExactly(id);
        return id;
    }

    @Test
    @DisplayName("successful create publishes exactly one event for the created reservation")
    void successfulCreatePublishesOneEventForTheCreatedReservation() throws Exception {
        JsonNode created = json(create(
                requestBody(workspaceId, "user-1", "2026-10-02T09:00:00Z", "2026-10-02T10:00:00Z"), 201));

        assertThat(brokerDouble.history())
                .extracting(ProducerRecord::key)
                .containsExactly(created.get("id").asText());
    }

    @Test
    @DisplayName("create rejected by request validation (400) publishes nothing")
    void createRejectedByRequestValidationPublishesNothing() throws Exception {
        String publishedId = createAndAssertOnePublished();

        create(requestBody("", "user-2", "2026-10-02T11:00:00Z", "2026-10-02T12:00:00Z"), 400);

        assertThat(brokerDouble.history())
                .extracting(ProducerRecord::key)
                .containsExactly(publishedId);
    }

    @Test
    @DisplayName("create rejected by time-range validation (400) publishes nothing")
    void createRejectedByTimeRangeValidationPublishesNothing() throws Exception {
        String publishedId = createAndAssertOnePublished();

        create(requestBody(workspaceId, "user-2", "2026-10-02T12:00:00Z", "2026-10-02T12:00:00Z"), 400);

        assertThat(brokerDouble.history())
                .extracting(ProducerRecord::key)
                .containsExactly(publishedId);
    }

    @Test
    @DisplayName("create rejected by conflict detection (409) publishes nothing")
    void createRejectedByConflictPublishesNothing() throws Exception {
        String publishedId = createAndAssertOnePublished();

        create(requestBody(workspaceId, "user-2", "2026-10-02T09:30:00Z", "2026-10-02T10:30:00Z"), 409);

        assertThat(brokerDouble.history())
                .extracting(ProducerRecord::key)
                .containsExactly(publishedId);
    }

    @Test
    @DisplayName("publish failure changes neither the 201 response nor the stored reservation")
    void publishFailureDoesNotChangeCreationResponseOrStoredReservation() throws Exception {
        brokerDouble.sendException =
                new TimeoutException("Topic reservation-events not present in metadata after 1000 ms.");
        long failuresBefore = publisher.failedPublishCount();

        JsonNode created = json(create(
                requestBody(workspaceId, "user-1", "2026-10-02T09:00:00Z", "2026-10-02T10:00:00Z"), 201));

        assertThat(publisher.failedPublishCount()).isEqualTo(failuresBefore + 1);
        List<String> fieldNames = new ArrayList<>();
        created.fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames).containsExactlyInAnyOrder(
                "id", "workspaceId", "userId", "startTime", "endTime", "status");
        String id = created.get("id").asText();
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(created.get("workspaceId").asText()).isEqualTo(workspaceId);
        assertThat(created.get("userId").asText()).isEqualTo("user-1");
        assertThat(created.get("startTime").asText()).isEqualTo("2026-10-02T09:00:00Z");
        assertThat(created.get("endTime").asText()).isEqualTo("2026-10-02T10:00:00Z");
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");

        JsonNode retrieved = json(mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(retrieved).isEqualTo(created);

        create(requestBody(workspaceId, "user-2", "2026-10-02T09:30:00Z", "2026-10-02T10:30:00Z"), 409);
    }
}
```

## Expected RED Evidence

**Expected / Predicted — Not Yet Verified.**

- **Compilation:** the new test compiles. Every type it uses exists: `ReservationEventPublisher`
  (RE-M4), `MockProducer` (RE-M2), Spring test support. This RED is therefore a runtime-assertion
  RED, not a compilation RED.
- **`mvn test`:** `Tests run: 94`. The 5 new tests fail with AssertJ assertion failures:
  - tests 1–4: `history()` keys expected `[<id>]` but were `[]`;
  - test 5: `failedPublishCount` expected `n+1` but was `n`.

  Every failure is attributable to the absent trigger on the creation path. None is caused by
  compilation, configuration, dependencies, the environment, or a missing broker. All 89 existing
  tests, including RE-M3's 13, pass (subject to RE-G6).
- **`mvn verify`:** fails at the `test` phase with the same 5 failures.
- **Satisfiability check (planning time and V3):** the tests must be able to pass. In a scratch copy
  **outside the repository**, a single throwaway line calling the publisher after the store write
  (the V3 probe below) must make all 94 pass, subject to RE-G6. This shows the tests are satisfiable
  and do not over-constrain RE-M6. It does not define RE-M6's design.

*Planning-time dry run (2026-09-28), in scratch copies outside the repository. This is not RED
evidence; RE-M5 has not been executed.* The test was extracted from this document.

- **Copy with the test only:** `Tests run: 94, Failures: 5`. Exactly the 5 new tests failed.
  - Tests 1–4: `Expecting actual: [] to contain exactly …`.
  - Test 5: `expected: 1L but was: 0L`.
  - Every other test passed. Total time 28.0 s.
- **Copy with the probe diff:** `Tests run: 94, Failures: 0`, BUILD SUCCESS. Total time 48.1 s. The
  roughly 20 s increase is the measured RE-V3 cost of the existing HTTP tests calling the real
  producer with no broker.
- The probe diff above is the `git diff` taken from that copy.

## Acceptance / Completion Criteria

RE-M5 is complete when all of the following are true:

1. Exactly one file is added, `ReservationCreationEventPublishingTest.java`, identical to Exact Code.
   No production, configuration, build, or existing test file changes (excluding the `Plan.md`
   Execution Status update).
2. `mvn test` and `mvn verify` in the repository report exactly the 5 new tests failing. Each failure
   is an assertion failure from the missing creation-path trigger, as tabulated above.
3. Every other test passes, 89 in all. Carried from RE-G6: a run whose only additional failure is
   `ReservationStoreConflictTest#testConcurrentCreatesMixedScenarioCorrectConflictDetection` does not
   block, and that result is recorded.
4. No failure is caused by compilation, configuration, dependencies, the environment, or a missing
   broker.
5. The V3 probe, in a scratch copy only, makes all 5 new tests pass, and every other test passes
   (subject to RE-G6).
6. No broker is present (V0). Only in-process doubles are used (RE-R17).

## Verification Commands

`<repo>` is the repository root. `$S` is this session's scratchpad. All results are **Expected /
Predicted — Not Yet Verified**.

| Step | Command (bash) | Expected |
| --- | --- | --- |
| V0 | `powershell -Command "(Test-NetConnection localhost -Port 9092 -WarningAction SilentlyContinue).TcpTestSucceeded"` | `False` |
| V1 | `cd <repo> && mvn -B test` | `Tests run: 94, Failures: 5, Errors: 0`. The 5 failures are exactly the new tests, with the messages above. BUILD FAILURE. |
| V2 | `cd <repo> && mvn -B verify` | Same as V1 |
| V3 | `rm -rf $S/rem5-probe && mkdir -p $S/rem5-probe && tar --exclude=./target --exclude=./.git -cf - -C <repo> . \| tar -xf - -C $S/rem5-probe`. Then apply the probe diff below to `$S/rem5-probe` with `git apply`, then `cd $S/rem5-probe && mvn -B test`. | `Tests run: 94, Failures: 0, Errors: 0`, subject to RE-G6. BUILD SUCCESS. |
| V4 | `cd <repo> && git status --short` | Exactly the RE-M2/M3/M4 entries plus the new `ReservationCreationEventPublishingTest.java` inside `src/test/java/com/example/workspace/reservation/controller/`, which shows as `?? src/test/java/com/example/workspace/reservation/controller/ReservationCreationEventPublishingTest.java`. |
| V5 | Extract the Java block from this plan and `diff` it against the created file | Identical |

**V3 probe diff (scratch copy only; never applied to the repository).** It exists only to
demonstrate that the tests can pass:

```diff
--- a/src/main/java/com/example/workspace/reservation/controller/ReservationController.java
+++ b/src/main/java/com/example/workspace/reservation/controller/ReservationController.java
@@ -23,6 +23,8 @@ public class ReservationController {
 
     private final ReservationValidator validator;
     private final ReservationStore store;
+    @Autowired
+    private com.example.workspace.reservation.event.ReservationEventPublisher probePublisher;
 
     @Autowired
     public ReservationController(ReservationValidator validator, ReservationStore store) {
@@ -61,6 +63,7 @@ public class ReservationController {
 
         // Store reservation with conflict detection
         Reservation storedReservation = store.createWithConflictCheck(reservation);
+        probePublisher.publishReservationCreated(storedReservation);
 
         // Return 201 Created with response body
         return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(storedReservation));
```

**Repeatability and isolation:**
- The new test shares one cached Spring context across its 5 tests. It isolates them by clearing
  `MockProducer` history and `sendException` in `@BeforeEach`, using a fresh random `workspaceId` per
  test (the in-memory store persists within the context), and asserting the counter as a delta.
- The in-memory store is the only persistent state it touches.
- Verification runs the new test in the same JVM as the existing tests (V1 and V2), and V3 repeats it
  in another copy.

## Pre-authorized Contingencies

None. Any other failure stops execution and returns to planning.

## Rollback / Recovery

Delete only
`src/test/java/com/example/workspace/reservation/controller/ReservationCreationEventPublishingTest.java`,
and remove `$S/rem5-probe`. Do not use any repository-wide reset, clean, or checkout.

## Risks

1. **RE-V3 slowdown after RE-M6.** The existing HTTP tests will run the real producer with no broker.
   The planning-time dry run measured about +20 s per `mvn test` (28 s → 48 s). This is a forward
   risk that RE-M6 verification re-measures.
2. **Context isolation.** This class has its own context, a new one because of the nested
   `@TestConfiguration`. The real `KafkaProducer` bean is still constructed there (it is not
   `@Primary`) but receives no sends.
3. **The `@Primary` override depends on RE-G1's bean shape.** If RE-M6 changes how the publisher
   obtains its producer, this test's double might not be used. The RE-M6 plan must keep the RE-G1
   beans.

## Explicit Exclusions

- No production code, scaffolding, configuration, or dependency change. The V3 probe exists only in
  a scratch copy.
- No re-verification of the event payload (RE-M3).
- No change to existing tests (RE-V3).
- No Docker, embedded broker, or real broker (RE-X10).
- No test fixing where in the creation path the publish is triggered (RE-D1).
- No test for any event other than `ReservationCreated` (RE-X2).

## Human Review

- Status: **Approved** as written by human review (2026-09-28), including RE-V1–RE-V3. Approval
  authorizes RE-M5 execution only, not RE-M6.
