package kz.gymbooking.dto;

import jakarta.validation.constraints.Size;

public record UserUpdate(@Size(min = 1, max = 100) String fullName, String phone) {
}
