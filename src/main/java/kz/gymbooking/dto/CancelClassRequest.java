package kz.gymbooking.dto;

import jakarta.validation.constraints.Size;

public record CancelClassRequest(@Size(max = 200) String reason) {
}
