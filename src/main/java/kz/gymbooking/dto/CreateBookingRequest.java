package kz.gymbooking.dto;

import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(@NotNull Integer classId) {
}
