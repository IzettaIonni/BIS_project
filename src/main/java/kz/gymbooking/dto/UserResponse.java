package kz.gymbooking.dto;

import java.time.Instant;

import kz.gymbooking.domain.Role;

public record UserResponse(Long id, String email, String fullName, String phone, Role role, Instant createdAt) {
}
