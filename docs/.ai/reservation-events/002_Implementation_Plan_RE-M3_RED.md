# 002 — Implementation Plan — RE-M3 RED: Event publishing behavior — tests

## Milestone

- **Work item:** `reservation-events`
- **Milestone:** RE-M3, Event publishing behavior — tests
- **Milestone type (as recorded in `Plan.md`):** RED
- **Successor:** RE-M4 (GREEN)
- **Status:** Approved 2026-09-28 (see Human Review). **Executed 2026-09-28: valid RED established**,
  with all criteria met. See the `Plan.md` Execution Status entry for the evidence.

## Authoritative References

| Artifact | Status |
| --- | --- |
| `docs/.ai/reservation-events/requirements.md` | Approved 2026-09-28 |
| `docs/.ai/reservation-events/Plan.md` (§ RE-M3) | Approved 2026-09-28 |
| `docs/.ai/reservation-events/API-Contract.md` | Approved 2026-09-28 |
| `docs/.ai/reservation-events/001_Implementation_Plan_RE-M2_FOUNDATION.md` | Approved, executed, and verified 2026-09-28 |
| `CLAUDE.md` | Verification commands `mvn test`, `mvn verify` |

## Predecessor Evidence

`Plan.md` Execution Status records **RE-M2 Complete** (2026-09-28). The evidence:

- no broker was present;
- `mvn test` and `mvn verify` gave BUILD SUCCESS with 76/76 passing;
- `org.apache.kafka:kafka-clients:4.3.1` resolved at `compile` scope;
- `MockProducer` is present in the resolved jar;
- the application started and served HTTP.

RE-M1 is also recorded Complete, with an approved contract.

## Current Repository State (inspected 2026-09-28)

- `git status --short` shows:
  - `M pom.xml` and `M src/main/resources/application.properties` (RE-M2);
  - `?? docs/.ai/reservation-events/`.

  Nothing else has changed.
- There is no `com.example.workspace.reservation.event` package and no publisher or event type. The
  publishing behavior is absent, which is the intended RED condition.
- The domain factory `Reservation.create(String workspaceId, String userId, LocalDateTime start,
  LocalDateTime end)` assigns a random `UUID` id (`Reservation.java:25-28`).
- HTTP timestamp representation: `LocalDateTime` is treated as UTC, converted with
  `atZone(ZoneId.of("UTC")).toInstant()`, and formatted with `DateTimeFormatter.ISO_INSTANT`
  (`ReservationController.java:111-115`). The contract requires the event to use the same
  representation.
- Test stack already on the test classpath:
  - JUnit 5, AssertJ, and `OutputCaptureExtension` from `spring-boot-starter-test`;
  - Jackson `ObjectMapper` from `spring-boot-starter-web`;
  - `MockProducer` and `StringSerializer` from `kafka-clients` 4.3.1.
- `MockProducer` 4.3.1 behavior this plan relies on (source:
  `https://raw.githubusercontent.com/apache/kafka/4.3.1/clients/src/main/java/org/apache/kafka/clients/producer/MockProducer.java`):
  - **Constructor:** `MockProducer(boolean autoComplete, Partitioner, Serializer<K>, Serializer<V>)`
    uses `Cluster.empty()` and invokes both serializers on `send`, so they must be non-null.
  - **Synchronous failure:** the public field `sendException`, if set, is thrown from `send(...)`
    **before** the record is added to `history()` (lines 305–307).
  - **Asynchronous completion:** with `autoComplete=false`, `completeNext()` and
    `errorNext(RuntimeException)` complete the oldest pending send and invoke its callback on the
    calling thread. Each returns `false` when nothing is pending (lines 576–593).
  - **Flush:** `flush()` completes every pending send (lines 356–364).

## Carried-Forward Decisions

| Decision | Approved in |
| --- | --- |
| RE-F1: plain `kafka-clients` 4.3.1, no `spring-kafka` | `001_Implementation_Plan_RE-M2_FOUNDATION.md` |
| RE-F2: `MockProducer` is the in-process double for the broker boundary (RE-R17) | `001_…_FOUNDATION.md` |
| RE-F3 / RE-F4: `reservation-events.kafka.bootstrap-servers`, `reservation-events.kafka.max-block-ms=1000` | `001_…_FOUNDATION.md`. Not exercised by these unit tests; RE-M4 consumes them. |

## Decisions for Review

RED must fix the minimal production seam that the tests call. These decisions bind RE-M4 and RE-M5,
and are presented for approval. The labels `RE-T1`–`RE-T4` are introduced here and do not collide
with `RE-R`, `RE-O`, `RE-C`, `RE-X`, `RE-D`, `RE-M`, `RE-S`, `RE-K`, or `RE-F`.

| ID | Decision | Proposed | Alternatives | Why |
| --- | --- | --- | --- | --- |
| RE-T1 | Production seam the tests call (part of RE-D1) | `public class com.example.workspace.reservation.event.ReservationEventPublisher` with:<br>• `public ReservationEventPublisher(org.apache.kafka.clients.producer.Producer<String, String> producer)`<br>• `public void publishReservationCreated(Reservation reservation)`, which never throws for a publish failure<br>• `public long failedPublishCount()`, the internal failure count (RE-D2) | (a) Inject a Spring `@Component` and test through a Spring context. Heavier, and needs a producer bean in RED. (b) Use a `byte[]` value type. The contract's UTF-8 JSON encoding would then be the publisher's job rather than `StringSerializer`'s. | This is the smallest seam that lets `MockProducer` stand in for the broker (RE-R17). The counter accessor is the only way to observe "counted" without a metrics stack (RE-X9). |
| RE-T2 | How "logged" is observed (RE-R16, part of RE-D3) | A failed publish writes at least one log line at `WARN` or `ERROR` that contains the reservation id. Wording, logger name, and other fields are left to RE-M4. A successful publish writes no such `WARN`/`ERROR` line. The output is captured with Spring Boot's `OutputCaptureExtension`. | (a) Only assert that the output contains the id. A routine INFO line on every publish would then satisfy it vacuously. (b) Assert exact wording. That over-constrains RE-D3. | Makes "logged" falsifiable without fixing the wording. |
| RE-T3 | Publish does not wait for broker acknowledgment | `publishReservationCreated` returns once the record is handed to the producer. It neither blocks on the send's result nor calls `flush()`. A failure reported later, when the producer completes the send, is logged and counted at that time. The tests model this with `MockProducer(autoComplete=false)` plus `completeNext()` / `errorNext(...)`. | Wait synchronously on the send's result. With a real producer, a create request could then block for the client's `delivery.timeout.ms` (default 120 s) when the broker is slow. | RE-R8 and RE-R12 require a slow or unavailable broker to be absorbed. RE-M6 puts this call on the creation path, and RE-F4 already bounds the synchronous part to 1 s. |
| RE-T4 | Guard against hangs | Class-level `@Timeout(10)` (seconds) on the test class. | No timeout: an implementation that waits for acknowledgment would hang the build instead of failing. | Turns a violation of RE-T3 into a visible test failure. |

## Files in Scope

| File | Action |
| --- | --- |
| `src/test/java/com/example/workspace/reservation/event/ReservationEventPublisherTest.java` | Create |

No production, configuration, build, or existing test file changes. `Plan.md` Execution Status is
updated after verified execution.

## Ordered Proposed Changes

1. Create `ReservationEventPublisherTest.java` with exactly the 13 tests below (full content in Exact
   Code).

## Test → Requirement / Contract Mapping

| # | Test method | Verifies |
| --- | --- | --- |
| 1 | `publishesToReservationEventsTopic` | Contract: topic `reservation-events` (RE-C1) |
| 2 | `recordKeyIsReservationId` | Contract: record key is the reservation id in canonical UUID form (RE-C1) |
| 3 | `valueHasExactlyTheContractFieldsAndNoPersonalData` | Contract: the exact 8-field set, a JSON object value, and no `status` field. `userId` is the only user identification and there is no email or other personal data (RE-R7, RE-X6). |
| 4 | `envelopeFieldsMatchContract` | Contract: `eventType` = `"ReservationCreated"`, and `schemaVersion` is the JSON integer `1` |
| 5 | `reservationFieldsMatchCreatedReservation` | Contract: `reservationId`, `workspaceId`, `userId`, `startTime`, and `endTime` are strings equal to the reservation's values; whole-second times render as `…Z` |
| 6 | `timestampsUseSameRepresentationAsHttpResponses` | Contract § Timestamps: fractional seconds are rendered exactly as `ISO_INSTANT` renders them (`…09:00:00.500Z`) |
| 7 | `occurredAtIsUtcInstantOfPublish` | Contract: `occurredAt` is a UTC ISO-8601 instant, `Z`-suffixed, taken at the time of publishing. Second-level lower bound, so no precision is imposed. |
| 8 | `successfulPublishIsNotCountedOrLoggedAsFailure` | RE-R16 negative case; RE-T2 |
| 9 | `failureReportedAfterSendIsAbsorbedLoggedAndCounted` | RE-R8, RE-R12 (broker reports failure later), RE-R16; RE-T3 |
| 10 | `failureAtSendIsAbsorbedLoggedAndCounted` | RE-R8, RE-R12 (slow or unavailable broker: `TimeoutException` from `send`), RE-R16 |
| 11 | `eventRejectedAtSendIsNotRetriedOrPreserved` | RE-R10: no retry and no later redelivery of the failed event |
| 12 | `eventFailedAfterSendIsNotRetriedOrPreserved` | RE-R10: no retry and no later redelivery of the failed event |
| 13 | `eachFailedPublishIsCounted` | RE-R16: the count accumulates |

**Normative contract rules not tested here, with reasons:**

- **Trigger only on a successful create (201), not on 400 or 409** (RE-R4, RE-R5): owned by
  RE-M5/RE-M6.
- **UTF-8 encoding of key and value:** `MockProducer.history()` records the pre-serialization
  `String`. The byte encoding is performed by the `StringSerializer` configured on the real producer
  in RE-M4, whose default charset is UTF-8. This is not observable through the RE-T1 seam.
- **No record headers:** the contract only tells consumers not to rely on headers. It puts no
  obligation on the producer that these tests could assert.
- **Duplicates permitted, no exactly-once** (RE-R11): this is a permission, not a behavior. The Plan
  forbids asserting deduplication or exactly-once behavior, so no test asserts either.
- **No ordering guarantee:** the contract promises nothing, so there is nothing to assert.
- **Consumers ignore unknown fields:** consumer-side behavior (RE-X1).

## Exact Code

### `src/test/java/com/example/workspace/reservation/event/ReservationEventPublisherTest.java` (new, complete content)

```java
package com.example.workspace.reservation.event;

import com.example.workspace.reservation.domain.Reservation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * RED tests for reservation-events RE-M3: ReservationCreated publishing behavior.
 * The broker boundary is doubled in process with Kafka's MockProducer (RE-R17, RE-F2).
 */
@ExtendWith(OutputCaptureExtension.class)
@Timeout(10)
class ReservationEventPublisherTest {

    private static final Pattern WARN_OR_ERROR = Pattern.compile("\\b(WARN|ERROR)\\b");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockProducer<String, String> producer;
    private ReservationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        producer = new MockProducer<>(false, null, new StringSerializer(), new StringSerializer());
        publisher = new ReservationEventPublisher(producer);
    }

    private static Reservation reservation(String workspaceId, String userId) {
        return Reservation.create(workspaceId, userId,
                LocalDateTime.of(2026, 10, 2, 9, 0),
                LocalDateTime.of(2026, 10, 2, 10, 0));
    }

    private ProducerRecord<String, String> onlySentRecord() {
        assertThat(producer.history()).hasSize(1);
        return producer.history().get(0);
    }

    private JsonNode valueOf(ProducerRecord<String, String> record) throws Exception {
        return objectMapper.readTree(record.value());
    }

    private static boolean hasFailureLogLine(CapturedOutput output, String reservationId) {
        return output.getAll().lines()
                .anyMatch(line -> line.contains(reservationId) && WARN_OR_ERROR.matcher(line).find());
    }

    // --- Contract conformance (RE-C1, RE-R7) ---

    @Test
    void publishesToReservationEventsTopic() {
        publisher.publishReservationCreated(reservation("ws-42", "user-7"));

        assertThat(onlySentRecord().topic()).isEqualTo("reservation-events");
    }

    @Test
    void recordKeyIsReservationId() {
        Reservation reservation = reservation("ws-42", "user-7");

        publisher.publishReservationCreated(reservation);

        assertThat(onlySentRecord().key()).isEqualTo(reservation.getId().toString());
    }

    @Test
    void valueHasExactlyTheContractFieldsAndNoPersonalData() throws Exception {
        publisher.publishReservationCreated(reservation("ws-42", "user-7"));

        JsonNode value = valueOf(onlySentRecord());
        assertThat(value.isObject()).isTrue();
        List<String> fieldNames = new ArrayList<>();
        value.fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames).containsExactlyInAnyOrder(
                "eventType", "schemaVersion", "occurredAt", "reservationId",
                "workspaceId", "userId", "startTime", "endTime");
    }

    @Test
    void envelopeFieldsMatchContract() throws Exception {
        publisher.publishReservationCreated(reservation("ws-42", "user-7"));

        JsonNode value = valueOf(onlySentRecord());
        assertThat(value.get("eventType").isTextual()).isTrue();
        assertThat(value.get("eventType").asText()).isEqualTo("ReservationCreated");
        assertThat(value.get("schemaVersion").isInt()).isTrue();
        assertThat(value.get("schemaVersion").intValue()).isEqualTo(1);
    }

    @Test
    void reservationFieldsMatchCreatedReservation() throws Exception {
        Reservation reservation = reservation("ws-42", "user-7");

        publisher.publishReservationCreated(reservation);

        JsonNode value = valueOf(onlySentRecord());
        assertThat(value.get("reservationId").isTextual()).isTrue();
        assertThat(value.get("reservationId").asText()).isEqualTo(reservation.getId().toString());
        assertThat(value.get("workspaceId").isTextual()).isTrue();
        assertThat(value.get("workspaceId").asText()).isEqualTo("ws-42");
        assertThat(value.get("userId").isTextual()).isTrue();
        assertThat(value.get("userId").asText()).isEqualTo("user-7");
        assertThat(value.get("startTime").isTextual()).isTrue();
        assertThat(value.get("startTime").asText()).isEqualTo("2026-10-02T09:00:00Z");
        assertThat(value.get("endTime").isTextual()).isTrue();
        assertThat(value.get("endTime").asText()).isEqualTo("2026-10-02T10:00:00Z");
    }

    @Test
    void timestampsUseSameRepresentationAsHttpResponses() throws Exception {
        Reservation reservation = Reservation.create("ws-1", "user-1",
                LocalDateTime.of(2026, 10, 2, 9, 0, 0, 500_000_000),
                LocalDateTime.of(2026, 10, 2, 10, 0));

        publisher.publishReservationCreated(reservation);

        JsonNode value = valueOf(onlySentRecord());
        assertThat(value.get("startTime").asText()).isEqualTo("2026-10-02T09:00:00.500Z");
        assertThat(value.get("endTime").asText()).isEqualTo("2026-10-02T10:00:00Z");
    }

    @Test
    void occurredAtIsUtcInstantOfPublish() throws Exception {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        publisher.publishReservationCreated(reservation("ws-42", "user-7"));

        Instant after = Instant.now();
        JsonNode occurredAt = valueOf(onlySentRecord()).get("occurredAt");
        assertThat(occurredAt.isTextual()).isTrue();
        assertThat(occurredAt.asText()).endsWith("Z");
        assertThat(Instant.parse(occurredAt.asText())).isBetween(before, after);
    }

    // --- Best-effort delivery, failure handling, logging and counting (RE-R8, RE-R10, RE-R12, RE-R16) ---

    @Test
    void successfulPublishIsNotCountedOrLoggedAsFailure(CapturedOutput output) {
        Reservation reservation = reservation("ws-42", "user-7");

        publisher.publishReservationCreated(reservation);
        assertThat(producer.completeNext()).isTrue();

        assertThat(publisher.failedPublishCount()).isZero();
        assertThat(hasFailureLogLine(output, reservation.getId().toString())).isFalse();
    }

    @Test
    void failureReportedAfterSendIsAbsorbedLoggedAndCounted(CapturedOutput output) {
        Reservation reservation = reservation("ws-42", "user-7");

        assertThatCode(() -> publisher.publishReservationCreated(reservation)).doesNotThrowAnyException();
        assertThat(producer.errorNext(new TimeoutException("Expiring 1 record(s) for reservation-events-0")))
                .isTrue();

        assertThat(publisher.failedPublishCount()).isEqualTo(1);
        assertThat(hasFailureLogLine(output, reservation.getId().toString())).isTrue();
    }

    @Test
    void failureAtSendIsAbsorbedLoggedAndCounted(CapturedOutput output) {
        Reservation reservation = reservation("ws-42", "user-7");
        producer.sendException = new TimeoutException("Topic reservation-events not present in metadata after 1000 ms.");

        assertThatCode(() -> publisher.publishReservationCreated(reservation)).doesNotThrowAnyException();

        assertThat(producer.history()).isEmpty();
        assertThat(publisher.failedPublishCount()).isEqualTo(1);
        assertThat(hasFailureLogLine(output, reservation.getId().toString())).isTrue();
    }

    @Test
    void eventRejectedAtSendIsNotRetriedOrPreserved() {
        Reservation first = reservation("ws-1", "user-1");
        Reservation second = reservation("ws-2", "user-2");
        producer.sendException = new TimeoutException("Topic reservation-events not present in metadata after 1000 ms.");
        publisher.publishReservationCreated(first);
        producer.sendException = null;

        publisher.publishReservationCreated(second);
        assertThat(producer.completeNext()).isTrue();

        assertThat(producer.history())
                .extracting(ProducerRecord::key)
                .containsExactly(second.getId().toString());
        assertThat(producer.completeNext()).isFalse();
    }

    @Test
    void eventFailedAfterSendIsNotRetriedOrPreserved() {
        Reservation first = reservation("ws-1", "user-1");
        Reservation second = reservation("ws-2", "user-2");
        publisher.publishReservationCreated(first);
        assertThat(producer.errorNext(new TimeoutException("Expiring 1 record(s) for reservation-events-0")))
                .isTrue();

        publisher.publishReservationCreated(second);
        assertThat(producer.completeNext()).isTrue();

        assertThat(producer.history())
                .extracting(ProducerRecord::key)
                .containsExactly(first.getId().toString(), second.getId().toString());
        assertThat(producer.completeNext()).isFalse();
    }

    @Test
    void eachFailedPublishIsCounted() {
        producer.sendException = new TimeoutException("Topic reservation-events not present in metadata after 1000 ms.");

        publisher.publishReservationCreated(reservation("ws-1", "user-1"));
        publisher.publishReservationCreated(reservation("ws-2", "user-2"));

        assertThat(publisher.failedPublishCount()).isEqualTo(2);
    }
}
```

## Expected RED Evidence

**Expected / Predicted — Not Yet Verified.**

- **Failure class:** `mvn test` fails at `testCompile`. Every compilation error is in
  `ReservationEventPublisherTest.java` and is caused solely by the intentionally absent approved
  production type:
  - `package com.example.workspace.reservation.event` has no production class;
  - `cannot find symbol … class ReservationEventPublisher`.

  This is valid RED under the RED rule: the missing type is the approved behavior's seam (RE-T1).
  It is not an unrelated compilation, configuration, dependency, or environment failure, and not a
  missing broker.
- **Exit status:** `mvn verify` fails the same way at the same phase.
- **Not caused by a broken test:** in an isolated scratch copy **outside the repository**, compiling
  the tests against a throwaway stub with the RE-T1 signatures must succeed. All 13 new tests must
  then fail at runtime and the 76 existing tests must pass. This proves the tests compile once the
  type exists and that none of them passes without the behavior.
- **Existing tests unaffected:** in a second isolated scratch copy without the new test file,
  `mvn test` passes 76/76.

*Planning-time dry run (2026-09-28). This is not RED evidence; RE-M3 has not been executed.* The
Exact Code test and the V3 stub were extracted from this document into a scratch copy outside the
repository. It compiled and ran `Tests run: 89`: the 13 new tests failed (11 errors from
`UnsupportedOperationException`, and 2 assertion failures from `assertThatCode`), and the 76
existing tests passed. The repository was not modified.

## Acceptance / Completion Criteria

RE-M3 is complete when all of the following are true:

1. Exactly one file is added, `ReservationEventPublisherTest.java`, with content identical to Exact
   Code. No production, configuration, build, or existing test file changes (excluding the
   `Plan.md` Execution Status update).
2. `mvn test` and `mvn verify` in the repository fail at test compilation. Every compilation error is
   located in `ReservationEventPublisherTest.java` and refers only to the absent
   `com.example.workspace.reservation.event` package or `ReservationEventPublisher` type.
3. No failure is caused by an unrelated compilation, dependency, configuration, or environment
   problem, or by a missing broker.
4. With the scratch-only stub (V3), the tests compile. All 13 new tests fail, and all 76 existing
   tests pass.
5. Without the new test file (V4), all 76 existing tests pass.
6. No broker is present during verification (V0).
7. Tests use only the in-process double: `MockProducer`, with no Docker, embedded broker, or real
   broker (RE-R17).

## Verification Commands

`<repo>` is the repository root. `$S` is this session's scratchpad
(`C:\Users\avina\AppData\Local\Temp\claude\C--cc-work-order-management-service\528f462b-8a6c-4a94-8e00-75881b0d6bb6\scratchpad`).
All results are **Expected / Predicted — Not Yet Verified**.

| Step | Command (bash) | Expected |
| --- | --- | --- |
| V0 | `powershell -Command "(Test-NetConnection localhost -Port 9092 -WarningAction SilentlyContinue).TcpTestSucceeded"` | `False` |
| V1 | `cd <repo> && mvn -B test` | Exit ≠ 0, `COMPILATION ERROR` / `BUILD FAILURE` at `testCompile`. All `[ERROR] …\.java` lines point to `ReservationEventPublisherTest.java` and name only `ReservationEventPublisher` or the `event` package. |
| V2 | `cd <repo> && mvn -B verify` | Same as V1 |
| V3 | `rm -rf $S/rem3-stub && mkdir -p $S/rem3-stub && tar --exclude=./target --exclude=./.git -cf - -C <repo> . \| tar -xf - -C $S/rem3-stub`. Then write the stub below to `$S/rem3-stub/src/main/java/com/example/workspace/reservation/event/ReservationEventPublisher.java`, then `cd $S/rem3-stub && mvn -B test` | Compiles. `Tests run: 89`, with the 13 `ReservationEventPublisherTest` tests failing or erroring (`UnsupportedOperationException`) and the other 76 passing. BUILD FAILURE only because of those 13. |
| V4 | `rm -rf $S/rem3-base && mkdir -p $S/rem3-base && tar --exclude=./target --exclude=./.git -cf - -C <repo> . \| tar -xf - -C $S/rem3-base && rm $S/rem3-base/src/test/java/com/example/workspace/reservation/event/ReservationEventPublisherTest.java && cd $S/rem3-base && mvn -B test` | BUILD SUCCESS, 76/76 |
| V5 | `cd <repo> && git status --short` | Exactly: `M pom.xml`, `M src/main/resources/application.properties` (RE-M2), `?? docs/.ai/reservation-events/`, `?? src/test/java/com/example/workspace/reservation/event/` |

**Scratch-only compile-check stub for V3.** It is never written into the repository and is deleted
with `$S/rem3-stub`:

```java
package com.example.workspace.reservation.event;

import com.example.workspace.reservation.domain.Reservation;
import org.apache.kafka.clients.producer.Producer;

public class ReservationEventPublisher {

    public ReservationEventPublisher(Producer<String, String> producer) {
    }

    public void publishReservationCreated(Reservation reservation) {
        throw new UnsupportedOperationException("RE-M3 V3 compile-check stub");
    }

    public long failedPublishCount() {
        throw new UnsupportedOperationException("RE-M3 V3 compile-check stub");
    }
}
```

**Repeatability and isolation:** the new tests touch no persistent state and override no
configuration. Each test builds its own `MockProducer` and publisher, and `OutputCaptureExtension`
is per-test. The repeat-run rule therefore does not apply. V3 and V4 run in separate scratch copies,
so the repository is never modified by verification.

## Pre-authorized Contingencies

None. Any failure other than the expected RED class stops execution and returns to planning.

## Rollback / Recovery

Delete only
`src/test/java/com/example/workspace/reservation/event/ReservationEventPublisherTest.java` (and its
now-empty `event` directory). Remove `$S/rem3-stub` and `$S/rem3-base`. Do not use any
repository-wide reset, clean, or checkout.

## Risks

1. **RE-T1–RE-T4 bind RE-M4.** If rejected, the tests must be redesigned before RED is executed.
2. **Log capture depends on console logging.** `OutputCaptureExtension` captures `System.out` and
   `System.err`. Logback writes to the console under both Spring Boot's configuration and Logback's
   default configuration. If RE-M4 routes the failure log away from the console, the tests would
   fail, correctly, because the failure would not be observable as logged.
3. **`@Timeout(10)`** could flake on a severely overloaded machine. These tests do no I/O and
   normally run in milliseconds.
4. **The fractional-second assertion** (`.500Z`) mirrors JDK `ISO_INSTANT` output. It is the same
   formatter the HTTP layer uses, as the contract requires.

## Explicit Exclusions

- No production code, scaffolding, configuration, or dependency change. The V3 stub exists only in a
  scratch copy outside the repository.
- No tests of the creation path, HTTP responses, or stored state (RE-M5).
- No test of any event other than `ReservationCreated` (RE-X2).
- No Docker, embedded broker, `spring-kafka-test`, or real broker (RE-X10).
- No assertion of log wording, logger name, or exact level beyond `WARN`/`ERROR` (RE-D3, RE-T2).
- No assertion of deduplication or exactly-once behavior (RE-R11).
- No change to existing tests.

## Human Review

- Status: **Approved** as written by human review (2026-09-28), including RE-T1–RE-T4. Approval
  authorizes RE-M3 execution only, not RE-M4.
