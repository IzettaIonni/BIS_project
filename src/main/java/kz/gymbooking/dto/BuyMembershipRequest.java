package kz.gymbooking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record BuyMembershipRequest(@NotNull Long planId, LocalDate startDate) {
}
