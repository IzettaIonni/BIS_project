package kz.gymbooking.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(@NotBlank String token, String gate) {
}
