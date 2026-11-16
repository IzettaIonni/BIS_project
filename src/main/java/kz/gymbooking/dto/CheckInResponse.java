package kz.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import kz.gymbooking.domain.CheckInDecision;
import kz.gymbooking.domain.DenyReason;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckInResponse(CheckInDecision decision, DenyReason reason, UserSummary user,
                              MembershipResponse membership, BookingResponse booking, Long visitId) {
}
