# 003 — Implementation Plan — RE-M4 GREEN: Event publishing behavior

## Milestone

- **Work item:** `reservation-events`
- **Milestone:** RE-M4, Event publishing behavior
- **Milestone type (as recorded in `Plan.md`):** GREEN
- **Predecessor:** RE-M3 (RED). **Successor:** RE-M5 (RED)
- **Status:** Approved 2026-09-28 (see Human Review). **Executed and verified 2026-09-28**, with all
  criteria met. See the `Plan.md` Execution Status entry for the evidence.

## Authoritative References

| Artifact | Status |
| --- | --- |
| `docs/.ai/reservation-events/requirements.md` | Approved 2026-09-28 |
| `docs/.ai/reservation-events/Plan.md` (§ RE-M4) | Approved 2026-09-28 |
| `docs/.ai/reservation-events/API-Contract.md` | Approved 2026-09-28 |
| `001_Implementation_Plan_RE-M2_FOUNDATION.md` | Approved, executed, verified |
| `002_Implementation_Plan_RE-M3_RED.md` | Approved, executed, valid RED |

## Predecessor RED Evidence

`Plan.md` records **RE-M3 Complete (valid RED)** on 2026-09-28:

- **Repository run:** `mvn test` and `mvn verify` fail at `testCompile`. There are 4 errors, all in
  `ReservationEventPublisherTest.java`, all `cannot find symbol: class ReservationEventPublisher`.
- **Against the scratch-only stub:** 89 tests run. The 13 new tests fail and the 76 existing tests
  pass.

## Current Repository State (inspected 2026-09-28)

- `git status --short` shows exactly:
  - `M pom.xml` and `M src/main/resources/application.properties` (RE-M2);
  - `?? docs/.ai/reservation-events/`;
  - `?? src/test/java/com/example/workspace/reservation/event/` (RE-M3).
- The `com.example.workspace.reservation.event` package has no production class.
- `@SpringBootApplication` is on `com.example.workspace.reservation.WorkspaceReservationApplication`.
  Component scanning therefore covers `…reservation.event`.
- The existing beans are `ReservationValidator` (`@Component`), `ReservationStore` (`@Component`), and
  `ReservationController`. No `@Configuration` class exists.
- `application.properties` defines `reservation-events.kafka.bootstrap-servers=localhost:9092` and
  `reservation-events.kafka.max-block-ms=1000` (RE-M2). Nothing reads them yet.
- On the classpath:
  - `kafka-clients` 4.3.1 (RE-M2);
  - Jackson `jackson-databind` 2.17.2, managed by the Spring Boot 3.3.3 BOM via
    `spring-boot-starter-web`;
  - SLF4J 2.0.16.
- The HTTP layer formats times as `ReservationController.java:111-115` shows:
  `atZone(ZoneId.of("UTC")).toInstant()` followed by `DateTimeFormatter.ISO_INSTANT`.

## Carried-Forward Decisions

| Decision | Approved in | How this plan honors it |
| --- | --- | --- |
| RE-F1: `kafka-clients` 4.3.1, no `spring-kafka` | 001 | Uses `KafkaProducer` directly; adds no dependency |
| RE-F2: `MockProducer` is the test double | 001 | Unchanged; the RE-M3 tests are used as-is |
| RE-F3: `reservation-events.kafka.bootstrap-servers` | 001 | Read into `bootstrap.servers` |
| RE-F4: `reservation-events.kafka.max-block-ms=1000` | 001 | Read into `max.block.ms` |
| RE-T1: class, constructor, and method signatures | 002 | Implemented exactly |
| RE-T2: a failure is logged at `WARN`/`ERROR` with the reservation id; no such line on success | 002 | Logs `WARN` with the id on failure only |
| RE-T3: no waiting for acknowledgment, no `flush()`; a later failure is logged and counted when reported | 002 | Uses `send(record, callback)` and never reads the `Future` |
| RE-T4: `@Timeout(10)` | 002 | Test unchanged |

## Decisions for Review

The labels `RE-G1`–`RE-G4` are introduced here. They do not collide with `RE-R`, `RE-O`, `RE-C`,
`RE-X`, `RE-D`, `RE-M`, `RE-S`, `RE-K`, `RE-F`, or `RE-T`.

| ID | Decision | Proposed | Alternatives | Why |
| --- | --- | --- | --- | --- |
| RE-G1 | Where the real producer and the publisher are created | Include in RE-M4 a `@Configuration` class that builds a `KafkaProducer<String, String>` from the RE-F3/RE-F4 properties and exposes it as a `ReservationEventPublisher` bean. Nothing injects the publisher yet. The creation path is untouched until RE-M6. | Defer bean wiring to RE-M6. RE-M4 would then deliver a publisher that nothing in the application can use to reach Kafka, and RE-M6 would have to add configuration alongside the trigger. | Plan § RE-M4's deliverable is "publishes … to Kafka", and 001 states that RE-M4 consumes RE-F3/RE-F4. The existing `@SpringBootTest` classes, which start the full context with no broker, then verify RE-O3 for the new beans. The RE-M3 unit tests do not exercise this class. |
| RE-G2 | How the JSON value is built | Jackson `ObjectNode` with the 8 contract fields, serialized with `toString()`. Since Jackson 2.10, `JsonNode.toString()` produces valid JSON using default settings. | (a) A Java `record` serialized by `ObjectMapper.writeValueAsString`. That adds a type and a checked exception. (b) String concatenation, which risks broken escaping of `workspaceId`/`userId`. | Correct JSON escaping, with no new type and no checked exception. |
| RE-G3 | Failure logging and counting (RE-D2, RE-D3) | Count in an `AtomicLong` inside the publisher, exposed only through `failedPublishCount()`, so it stays internal (RE-X9). Log one `WARN` line: `Failed to publish ReservationCreated for reservation {id} (failed publishes so far: {n}): {exception}`. The exception is shown via `toString()`, with no stack trace. | `ERROR` level, or a stack trace. A broker outage would flood the logs, and RE-R8 says the failure is expected and absorbed. | Satisfies RE-T2 and RE-R16. It is thread-safe because a callback failure runs on the producer's I/O thread while a send failure runs on the request thread. |
| RE-G4 | Producer settings | Only `bootstrap.servers` (RE-F3), `max.block.ms` (RE-F4), and `StringSerializer` for key and value (UTF-8 by default, as the contract requires). Every other setting is the client default. In kafka-clients 4.x that includes `acks=all`, `enable.idempotence=true`, and `delivery.timeout.ms=120000`. | Tune `acks`, retries, or delivery timeout. Nothing in the requirements asks for this, and RE-R11 already tolerates duplicates. | This is the smallest configuration that satisfies the contract and RE-O3. |

These arose from the planning-time dry run. The user decided both on 2026-09-28.

| ID | Decision | Resolution | Alternatives rejected |
| --- | --- | --- | --- |
| RE-G5 | Background connection `WARN`s with no broker (Risk 5) | **Accept the client defaults.** No reconnect-backoff setting is added. The noise appears only while no broker is reachable. | Add `reconnect.backoff.max.ms`: a setting no requirement asks for, and slower reconnection when the broker returns. |
| RE-G6 | Pre-existing flaky `ReservationStoreConflictTest#testConcurrentCreatesMixedScenarioCorrectConflictDetection` (Risk 6) | **Record, don't block.** Criterion 3 is amended at planning time: this one named test's result is recorded in the evidence but does not block RE-M4. Every other test must pass. Fixing it is separate work outside `reservation-events`, and it will be listed at Final Review. | Keep criterion 3 strict (stop on a flake). Fix the test first (pause RE-M4). |

## Files in Scope

| File | Action |
| --- | --- |
| `src/main/java/com/example/workspace/reservation/event/ReservationEventPublisher.java` | Create |
| `src/main/java/com/example/workspace/reservation/event/ReservationEventsConfiguration.java` | Create |

No test, build, or configuration file changes. The RE-M3 test file is not modified. `Plan.md`
Execution Status is updated after verified execution.

## Ordered Proposed Changes

1. Create `ReservationEventPublisher` with exactly the RE-T1 surface:
   - **Fields:** `TOPIC = "reservation-events"`, an SLF4J logger, the injected
     `Producer<String, String>`, and an `AtomicLong` failure counter.
   - **`publishReservationCreated`:** builds a `ProducerRecord` for topic `reservation-events` with
     the reservation id as key and the JSON value (RE-G2), then calls
     `producer.send(record, callback)`. The callback records a failure when its `exception` is
     non-null. A `RuntimeException` thrown by building or sending the record is caught and recorded.
     The method never throws.
   - **`failedPublishCount()`:** returns the counter.
   - **Private `toJson`:** builds the 8 contract fields in contract order. `occurredAt` is
     `ISO_INSTANT` of `Instant.now()`. `startTime` and `endTime` are converted exactly as the
     controller does.
   - **Private `recordFailure`:** increments the counter and logs the RE-G3 `WARN` line.
2. Create `ReservationEventsConfiguration` (`@Configuration`) with two beans:
   - `Producer<String, String> reservationEventsProducer(...)`, which builds a `KafkaProducer` with
     `bootstrap.servers` from `${reservation-events.kafka.bootstrap-servers}`, `max.block.ms` from
     `${reservation-events.kafka.max-block-ms}`, and `StringSerializer` for key and value (RE-G1,
     RE-G4). It is closed on context shutdown through Spring's inferred `close()` destroy method.
   - `ReservationEventPublisher reservationEventPublisher(Producer<String, String>)` (RE-G1).

## Exact Code

### `src/main/java/com/example/workspace/reservation/event/ReservationEventPublisher.java` (new, complete content)

```java
package com.example.workspace.reservation.event;

import com.example.workspace.reservation.domain.Reservation;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes ReservationCreated events to Kafka as defined by the reservation-events API contract.
 *
 * Publishing is best-effort: a failure, whether thrown by send or reported later by the producer,
 * is logged and counted and never propagated to the caller. The caller does not wait for broker
 * acknowledgment, and failed events are neither retried nor kept.
 */
public class ReservationEventPublisher {

    static final String TOPIC = "reservation-events";

    private static final Logger log = LoggerFactory.getLogger(ReservationEventPublisher.class);

    private final Producer<String, String> producer;
    private final AtomicLong failedPublishCount = new AtomicLong();

    public ReservationEventPublisher(Producer<String, String> producer) {
        this.producer = producer;
    }

    public void publishReservationCreated(Reservation reservation) {
        String reservationId = reservation.getId().toString();
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, reservationId, toJson(reservation));
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    recordFailure(reservationId, exception);
                }
            });
        } catch (RuntimeException e) {
            recordFailure(reservationId, e);
        }
    }

    public long failedPublishCount() {
        return failedPublishCount.get();
    }

    private static String toJson(Reservation reservation) {
        ObjectNode event = JsonNodeFactory.instance.objectNode();
        event.put("eventType", "ReservationCreated");
        event.put("schemaVersion", 1);
        event.put("occurredAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        event.put("reservationId", reservation.getId().toString());
        event.put("workspaceId", reservation.getWorkspaceId());
        event.put("userId", reservation.getUserId());
        event.put("startTime", toUtcInstantString(reservation.getStartTime()));
        event.put("endTime", toUtcInstantString(reservation.getEndTime()));
        return event.toString();
    }

    // Same representation as the HTTP responses: LocalDateTime is treated as UTC, formatted as ISO_INSTANT.
    private static String toUtcInstantString(LocalDateTime time) {
        return DateTimeFormatter.ISO_INSTANT.format(time.atZone(ZoneId.of("UTC")).toInstant());
    }

    private void recordFailure(String reservationId, Exception exception) {
        long failures = failedPublishCount.incrementAndGet();
        log.warn("Failed to publish ReservationCreated for reservation {} (failed publishes so far: {}): {}",
                reservationId, failures, exception.toString());
    }
}
```

### `src/main/java/com/example/workspace/reservation/event/ReservationEventsConfiguration.java` (new, complete content)

```java
package com.example.workspace.reservation.event;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer and ReservationCreated publisher. No broker is needed to start: with no broker, the
 * producer keeps retrying its bootstrap connection in the background, and a send that cannot obtain
 * metadata fails after max.block.ms.
 */
@Configuration
public class ReservationEventsConfiguration {

    @Bean
    public Producer<String, String> reservationEventsProducer(
            @Value("${reservation-events.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${reservation-events.kafka.max-block-ms}") long maxBlockMs) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs);
        return new KafkaProducer<>(config, new StringSerializer(), new StringSerializer());
    }

    @Bean
    public ReservationEventPublisher reservationEventPublisher(Producer<String, String> reservationEventsProducer) {
        return new ReservationEventPublisher(reservationEventsProducer);
    }
}
```

## Acceptance / Completion Criteria

RE-M4 is complete when all of the following are true:

1. Exactly the two files above are created, with content identical to Exact Code. No other
   production, test, build, or configuration file changes (excluding the `Plan.md` Execution Status
   update).
2. All 13 RE-M3 tests pass without modification.
3. All 76 pre-existing tests pass without modification (89 in total), with one exception (RE-G6).
   The result of the known pre-existing order-dependent test
   `ReservationStoreConflictTest#testConcurrentCreatesMixedScenarioCorrectConflictDetection` is
   recorded but does not block. If it fails, the evidence must show it is the **only** failing test.
4. `mvn test` and `mvn verify` complete successfully with no broker present (RE-O3, RE-R18). A run
   whose **only** failure is the RE-G6 test does not block. It is recorded, together with the
   surefire summary showing every other test passing.
5. The application starts with no broker present, creates the producer, serves HTTP, and logs no
   `ERROR` line.
6. `ReservationController`, `ReservationStore`, and every other existing production class are
   unchanged. No publish is triggered from the creation path (RE-M6).
7. No excluded capability is introduced: no retry, outbox, or dead letter (RE-X3); no exactly-once
   or transactions (RE-X4); no metrics exporter (RE-X9); no other event (RE-X2); no personal data
   (RE-X6); no new dependency.

## Verification Commands

`<repo>` is the repository root. All results are **Expected / Predicted — Not Yet Verified**.

| Step | Command (bash) | Expected |
| --- | --- | --- |
| V0 | `powershell -Command "(Test-NetConnection localhost -Port 9092 -WarningAction SilentlyContinue).TcpTestSucceeded"` | `False` |
| V1 | `cd <repo> && mvn -B test` | BUILD SUCCESS. `Tests run: 89, Failures: 0, Errors: 0, Skipped: 0`, including `ReservationEventPublisherTest` 13/13. Under RE-G6, the only permitted non-blocking alternative is `Failures: 1`, where that one failure is `testConcurrentCreatesMixedScenarioCorrectConflictDetection`. |
| V2 | `cd <repo> && mvn -B verify` | BUILD SUCCESS, 89/89. The same RE-G6 non-blocking alternative as V1 applies. |
| V3 | `cd <repo> && mvn -B spring-boot:run "-Dspring-boot.run.arguments=--server.port=0"` until `Started WorkspaceReservationApplication` (≤ 60 s), then `GET /api/v1/reservations/00000000-0000-0000-0000-000000000000`, then stop the forked JVM by its logged PID | Started. The log contains `ProducerConfig values` showing `bootstrap.servers = [localhost:9092]` and `max.block.ms = 1000`. HTTP returns `404`. No `ERROR` log line. `WARN` lines from `org.apache.kafka.clients.NetworkClient` ("Connection to node -1 … could not be established") **are expected** while no broker is present (Risk 5). |
| V4 | `cd <repo> && git status --short && git diff --stat` | Exactly: `M pom.xml`, `M src/main/resources/application.properties`, `?? docs/.ai/reservation-events/`, `?? src/main/java/com/example/workspace/reservation/event/`, `?? src/test/java/com/example/workspace/reservation/event/`. The tracked diff is limited to the two RE-M2 files. |
| V5 | Extract both Java blocks from this plan and `diff` them against the created files | Identical |

**Repeatability and isolation:**
- No test touches persistent state or overrides configuration. The Kafka producer created by the
  `@SpringBootTest` contexts holds only in-memory state and is closed with the context. The
  repeat-run rule therefore does not apply.
- V3 uses `--server.port=0`, so it needs no fixed port.

## Pre-authorized Contingencies

None. Any failure stops execution and returns to planning.

## Rollback / Recovery

Delete only `src/main/java/com/example/workspace/reservation/event/ReservationEventPublisher.java`
and `ReservationEventsConfiguration.java`. Do not use any repository-wide reset, clean, or checkout.

## Risks

1. **Producer created in every application context (RE-G1).**
   - The existing `@SpringBootTest` classes will now construct a real `KafkaProducer` with no broker
     present.
   - `KafkaProducer` construction does not wait for a broker, so startup is unaffected (V1–V3
     verify this). It does start background bootstrap connection attempts (Risk 5).
   - An unresolvable `bootstrap-servers` host would fail startup with a `ConfigException`. The
     default `localhost` resolves.
2. **Forward risk for RE-M5/RE-M6, which does not affect RE-M4.** Once RE-M6 wires the publisher into
   creation, each create with no broker blocks for up to `max.block.ms` (1 s, RE-F4) before the send
   fails, is absorbed, and is logged. The existing HTTP tests create many reservations, so they would
   slow down unless RE-M5/RE-M6 substitute the publisher in those tests. To be decided in the RE-M5
   plan.
3. **Shutdown with pending records.** `close()` waits for in-flight records, bounded by
   `delivery.timeout.ms` (120 s default). With no broker, `send` fails at `max.block.ms` before a
   record is queued, so nothing is pending. With a slow but reachable broker, shutdown could wait for
   up to that timeout. This is accepted under RE-R10 (unsent events may be lost), and no tuning is
   requested.
4. **Log noise when a broker is unreachable.** One `WARN` per failed publish (RE-G3). No alerting is
   in scope (RE-X9).
5. **Background connection WARNs with no broker.**
   - *What was observed:* in a planning-time dry run in a scratch copy on 2026-09-28, the producer's
     I/O thread retried the bootstrap connection continuously (client default backoff, 50 ms rising
     to 1 s). `NetworkClient` logged about 4 `WARN` lines per second while idle, and 168 during a
     full `mvn test`.
   - *What was ruled out:* setting `enable.metrics.push=false` did not change this, so it is not
     client telemetry.
   - *Effect:* this is not a functional failure. The app starts, serves HTTP, and no `ERROR` is
     logged.
   - *Decision needed:* whether to accept it or tune it (for example `reconnect.backoff.max.ms`) is
     RE-G5 below.
6. **Pre-existing flaky test, unrelated to this work item.**
   `ReservationStoreConflictTest#testConcurrentCreatesMixedScenarioCorrectConflictDetection`
   (`ReservationStoreConflictTest.java:539-617`) is order-dependent:
   - Reservation ends are inclusive, so T1 (10:00–11:00) and T3 (11:00–12:00) overlap at 11:00.
   - When T2 (10:30–11:30) wins the race, only T2 and T4 succeed, and the assertion `3` fails with
     `2`.
   - On the RE-M2 baseline with no RE-M3/RE-M4 code, it failed **12 of 30** isolated runs
     (planning-time check in a scratch copy, 2026-09-28).
   - It failed once in the RE-M4 dry run. It passed in every earlier full-suite run.
   - It exercises only `ReservationStore`, which RE-M4 does not touch. Its handling is RE-G6 below.

## Explicit Exclusions

- No change to `ReservationController`, `ReservationStore`, or any creation-path code, and no
  injection of the publisher (RE-M6).
- No change to any test, including the RE-M3 tests (RE-T1–RE-T4 are honored as written).
- No new dependency, plugin, or configuration key. Only RE-F3 and RE-F4 are read.
- No retry, outbox, buffering, or dead letter (RE-X3). No transactions or exactly-once (RE-X4).
- No metrics backend, Actuator, or exposure of the count outside the application (RE-X9).
- No event other than `ReservationCreated` (RE-X2), and no personal data (RE-X6).
- No topic-name configuration; the topic is fixed by the contract.

## Human Review

- Status: **Approved** as written by human review (2026-09-28), including RE-G1–RE-G6. Approval
  authorizes RE-M4 execution only, not RE-M5.
