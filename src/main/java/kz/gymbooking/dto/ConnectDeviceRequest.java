package kz.gymbooking.dto;

import jakarta.validation.constraints.NotBlank;

public record ConnectDeviceRequest(@NotBlank String provider, String name) {
}
