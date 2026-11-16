package kz.gymbooking.dto;

public record TokenPairResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
}
