package com.example.workspace.reservation.store;

import com.example.workspace.reservation.domain.Reservation;
import com.example.workspace.reservation.domain.ReservationStatus;
import com.example.workspace.reservation.exception.ConflictException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ReservationStore {
    private final Map<UUID, Reservation> reservations = new ConcurrentHashMap<>();

    public Reservation create(Reservation reservation) {
        reservations.put(reservation.getId(), reservation);
        return reservation;
    }

    public Optional<Reservation> findById(UUID id) {
        return Optional.ofNullable(reservations.get(id));
    }

    public void cancel(UUID id) {
        Optional<Reservation> reservation = findById(id);
        reservation.ifPresent(Reservation::cancel);
    }

    /**
     * Create a reservation with conflict detection.
     *
     * Atomically checks for overlapping ACTIVE reservations in the same workspace
     * and creates the reservation if no conflict exists. If a conflict is detected,
     * throws ConflictException and does not create the reservation.
     *
     * This method is synchronized to ensure atomic check-and-create behavior,
     * preventing race conditions where concurrent requests could both succeed
     * when creating overlapping ACTIVE reservations for the same workspace.
     *
     * @param reservation the reservation to create
     * @return the created reservation with status ACTIVE
     * @throws ConflictException if an ACTIVE reservation exists for the same workspace
     *                          with overlapping time range
     */
    public synchronized Reservation createWithConflictCheck(Reservation reservation)
            throws ConflictException {

        // Check for conflict: find any ACTIVE reservation for the same workspace that overlaps
        boolean hasConflict = hasConflictingReservation(
            reservation.getWorkspaceId(),
            reservation.getStartTime(),
            reservation.getEndTime()
        );

        if (hasConflict) {
            throw new ConflictException(
                "CONFLICT",
                "conflict: An ACTIVE reservation already exists for workspace '" +
                reservation.getWorkspaceId() + "' during the requested time range [" +
                reservation.getStartTime() + ", " + reservation.getEndTime() + "]"
            );
        }

        // No conflict: safe to create
        return create(reservation);
    }

    /**
     * Check if a conflicting reservation exists for the given workspace and time range.
     *
     * A conflict exists if:
     * 1. An ACTIVE reservation exists for the same workspace AND
     * 2. The time ranges overlap per the rule: newStart < existingEnd AND existingStart < newEnd
     *
     * CANCELLED reservations do not contribute to conflicts.
     *
     * @param workspaceId the workspace identifier
     * @param newStart the start time of the potential new reservation
     * @param newEnd the end time of the potential new reservation
     * @return true if a conflicting ACTIVE reservation exists, false otherwise
     */
    private boolean hasConflictingReservation(String workspaceId, LocalDateTime newStart, LocalDateTime newEnd) {
        return reservations.values().stream()
            .filter(r -> r.getStatus() == ReservationStatus.ACTIVE)
            .filter(r -> workspaceId.equals(r.getWorkspaceId()))
            .anyMatch(r -> overlaps(newStart, newEnd, r.getStartTime(), r.getEndTime()));
    }

    /**
     * Determine if two time ranges overlap.
     *
     * Overlap rule per requirements: s1 < e2 AND s2 < e1
     * - s1 (newStart) must be strictly before e2 (existingEnd)
     * - s2 (existingStart) must be strictly before e1 (newEnd)
     *
     * Adjacent times (one ends exactly when the other starts) do NOT overlap.
     * Example: [10:00, 11:00] and [11:00, 12:00] do not overlap.
     *
     * @param s1 start time of first range (new reservation)
     * @param e1 end time of first range (new reservation)
     * @param s2 start time of second range (existing reservation)
     * @param e2 end time of second range (existing reservation)
     * @return true if ranges overlap, false if they are adjacent or non-overlapping
     */
    private boolean overlaps(LocalDateTime s1, LocalDateTime e1, LocalDateTime s2, LocalDateTime e2) {
        return s1.isBefore(e2) && s2.isBefore(e1);
    }
}

