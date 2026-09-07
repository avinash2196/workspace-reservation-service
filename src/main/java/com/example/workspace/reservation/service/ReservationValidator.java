package com.example.workspace.reservation.service;

import com.example.workspace.reservation.dto.CreateReservationRequest;
import com.example.workspace.reservation.exception.ValidationException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationValidator {

    public void validateCreateReservationRequest(CreateReservationRequest request) {
        if (request.getWorkspaceId() == null || request.getWorkspaceId().isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "Field 'workspaceId' is required");
        }

        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "Field 'userId' is required");
        }

        if (request.getStartTime() == null || request.getStartTime().isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "Field 'startTime' is required");
        }

        if (request.getEndTime() == null || request.getEndTime().isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "Field 'endTime' is required");
        }

        LocalDateTime startTime = parseDateTime(request.getStartTime(), "startTime");
        LocalDateTime endTime = parseDateTime(request.getEndTime(), "endTime");

        if (!startTime.isBefore(endTime)) {
            throw new ValidationException("VALIDATION_ERROR",
                    "startTime must be strictly before endTime");
        }
    }

    public void validateUUID(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("VALIDATION_ERROR",
                    "Invalid UUID format: " + id);
        }
    }

    private LocalDateTime parseDateTime(String dateTimeString, String fieldName) {
        try {
            return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new ValidationException("VALIDATION_ERROR",
                    "Field '" + fieldName + "' must be a valid ISO 8601 datetime in UTC format");
        }
    }
}

