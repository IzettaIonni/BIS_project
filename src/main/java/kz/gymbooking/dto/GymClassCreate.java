package kz.gymbooking.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GymClassCreate(
        @NotBlank String name,
        @NotBlank String type,
        @NotNull Integer trainerId,
        @NotNull OffsetDateTime startTime,
        @NotNull @Min(15) @Max(240) Integer durationMin,
        @NotBlank String room,
        @NotNull @Min(1) Integer capacity) {
}
