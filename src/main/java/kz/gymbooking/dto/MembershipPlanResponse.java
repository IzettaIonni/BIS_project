package kz.gymbooking.dto;

public record MembershipPlanResponse(Long id, String name, int durationDays, Integer visitLimit, int price) {
}
