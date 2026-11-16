package kz.gymbooking.dto;

import kz.gymbooking.domain.CheckInDecision;
import kz.gymbooking.domain.DenyReason;

public record CheckInResponse(CheckInDecision decision, DenyReason reason, UserSummary user,
                              MembershipResponse membership, BookingResponse booking, Long visitId) {
}
