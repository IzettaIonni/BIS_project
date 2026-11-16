package kz.gymbooking.dto;

import jakarta.validation.constraints.NotNull;
import kz.gymbooking.domain.Role;

public record RoleChangeRequest(@NotNull Role role) {
}
