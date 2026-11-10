package kz.gymbooking.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GymClassUpdate(
        String name,
        Integer trainerId,
        OffsetDateTime startTime,
        @Min(15) @Max(240) Integer durationMin,
        String room,
        @Min(1) Integer capacity) {
}
