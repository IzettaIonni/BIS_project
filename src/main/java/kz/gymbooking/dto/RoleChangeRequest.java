package kz.gymbooking.dto;

import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull Role role) {
}
