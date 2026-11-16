package kz.gymbooking.dto;

import java.time.Instant;

public record QrTokenResponse(String token, Instant expiresAt) {
}
