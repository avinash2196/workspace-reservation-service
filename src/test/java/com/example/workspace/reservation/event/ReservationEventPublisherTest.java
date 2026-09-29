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
