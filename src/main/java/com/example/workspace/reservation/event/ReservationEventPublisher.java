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
