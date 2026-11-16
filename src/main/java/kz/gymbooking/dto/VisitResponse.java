package kz.gymbooking.dto;

import java.time.Instant;

public record VisitResponse(Long id, Long userId, String gate, Instant checkedInAt, Long bookingId) {
}
