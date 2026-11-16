package kz.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

import kz.gymbooking.domain.Role;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(Long id, String email, String fullName, String phone, Role role, Instant createdAt) {
}
