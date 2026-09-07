package com.example.workspace.reservation.controller;

import com.example.workspace.reservation.dto.CreateReservationRequest;
import com.example.workspace.reservation.dto.ReservationResponse;
import com.example.workspace.reservation.exception.ResourceNotFoundException;
import com.example.workspace.reservation.service.ReservationValidator;
import com.example.workspace.reservation.store.ReservationStore;
import com.example.workspace.reservation.domain.Reservation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationValidator validator;
    private final ReservationStore store;

    @Autowired
    public ReservationController(ReservationValidator validator, ReservationStore store) {
        this.validator = validator;
        this.store = store;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@RequestBody CreateReservationRequest request) {
        // Validate request fields and structure
        validator.validateCreateReservationRequest(request);

        // Parse datetime fields as Instant (UTC)
        Instant startTimeInstant = Instant.parse(request.getStartTime());
        Instant endTimeInstant = Instant.parse(request.getEndTime());

        // Validate time range (startTime < endTime)
        if (!startTimeInstant.isBefore(endTimeInstant)) {
            throw new com.example.workspace.reservation.exception.ValidationException(
                "VALIDATION_ERROR",
                "startTime must be strictly before endTime"
            );
        }

        // Convert Instant back to LocalDateTime (UTC) for domain model
        LocalDateTime startTime = LocalDateTime.ofInstant(startTimeInstant, ZoneId.of("UTC"));
        LocalDateTime endTime = LocalDateTime.ofInstant(endTimeInstant, ZoneId.of("UTC"));

        // Create reservation domain object
        Reservation reservation = Reservation.create(
                request.getWorkspaceId(),
                request.getUserId(),
                startTime,
                endTime
        );

        // Store reservation with conflict detection
        Reservation storedReservation = store.createWithConflictCheck(reservation);

        // Return 201 Created with response body
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(storedReservation));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable String id) {
        // Validate UUID format
        validator.validateUUID(id);

        UUID uuid = UUID.fromString(id);

        // Retrieve from store
        Reservation reservation = store.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException(id,
                        "Reservation with id '" + id + "' not found"));

        return ResponseEntity.ok(toResponse(reservation));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelReservation(@PathVariable String id) {
        // Validate UUID format
        validator.validateUUID(id);

        UUID uuid = UUID.fromString(id);

        // Check reservation exists before cancelling
        if (store.findById(uuid).isEmpty()) {
            throw new ResourceNotFoundException(id,
                    "Reservation with id '" + id + "' not found");
        }

        // Cancel reservation
        store.cancel(uuid);

        return ResponseEntity.noContent().build();
    }

    /**
     * Convert Reservation domain object to HTTP response DTO.
     *
     * Timestamps are formatted as ISO 8601 UTC strings (e.g., "2025-09-07T10:00:00Z").
     * LocalDateTime values in the domain model are treated as UTC for serialization.
     */
    private ReservationResponse toResponse(Reservation reservation) {
        // Convert LocalDateTime (stored as UTC) to Instant, then format as ISO 8601 UTC string
        Instant startInstant = reservation.getStartTime().atZone(ZoneId.of("UTC")).toInstant();
        Instant endInstant = reservation.getEndTime().atZone(ZoneId.of("UTC")).toInstant();

        String startTimeString = DateTimeFormatter.ISO_INSTANT.format(startInstant);
        String endTimeString = DateTimeFormatter.ISO_INSTANT.format(endInstant);

        return new ReservationResponse(
                reservation.getId().toString(),
                reservation.getWorkspaceId(),
                reservation.getUserId(),
                startTimeString,
                endTimeString,
                reservation.getStatus().toString()
        );
    }
}

