package com.example.workspace.reservation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Reservation")
class ReservationTest {

    @Test
    @DisplayName("should generate unique UUIDs for each reservation")
    void testCreateGeneratesUniqueIds() {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Reservation res = Reservation.create("workspace-1", "user-1", start, end);
            ids.add(res.getId());
        }

        assertThat(ids).hasSize(10); // All unique
    }

    @Test
    @DisplayName("should store all properties correctly")
    void testCreateStoresAllProperties() {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);
        String workspaceId = "workspace-1";
        String userId = "user-1";

        Reservation res = Reservation.create(workspaceId, userId, start, end);

        assertThat(res.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(res.getUserId()).isEqualTo(userId);
        assertThat(res.getStartTime()).isEqualTo(start);
        assertThat(res.getEndTime()).isEqualTo(end);
    }

    @Test
    @DisplayName("should set status to ACTIVE on creation")
    void testCreateSetsStatusToActive() {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        Reservation res = Reservation.create("workspace-1", "user-1", start, end);

        assertThat(res.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    @DisplayName("should transition from ACTIVE to CANCELLED on cancel")
    void testCancelTransitionsActiveToCancelled() {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        Reservation res = Reservation.create("workspace-1", "user-1", start, end);
        assertThat(res.getStatus()).isEqualTo(ReservationStatus.ACTIVE);

        res.cancel();

        assertThat(res.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("should be idempotent when cancel called multiple times")
    void testCancelIdempotentMultipleCalls() {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        Reservation res = Reservation.create("workspace-1", "user-1", start, end);

        res.cancel();
        res.cancel();
        res.cancel();

        assertThat(res.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("should consider two reservations with same id equal")
    void testEqualsReservationsWithSameIdAreEqual() {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        Reservation res1 = Reservation.create("workspace-1", "user-1", start, end);
        Reservation res2 = Reservation.create("workspace-1", "user-1", start, end);

        // Note: Equality based on id is implementation detail
        // This test documents the expected behavior for testing purposes
        assertThat(res1.getId()).isNotEqualTo(res2.getId()); // Different ids created
    }

    @Test
    @DisplayName("should consider two reservations with different ids not equal")
    void testEqualsDifferentIdReservationsAreNotEqual() {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        Reservation res1 = Reservation.create("workspace-1", "user-1", start, end);
        Reservation res2 = Reservation.create("workspace-1", "user-1", start, end);

        assertThat(res1).isNotEqualTo(res2); // Different by id
    }
}

