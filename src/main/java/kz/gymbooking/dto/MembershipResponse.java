package kz.gymbooking.dto;

import java.time.LocalDate;

import kz.gymbooking.domain.MembershipStatus;

public record MembershipResponse(Long id, Long clientId, MembershipPlanResponse plan, LocalDate startDate,
                                 LocalDate endDate, Integer visitsLeft, MembershipStatus status) {
}
