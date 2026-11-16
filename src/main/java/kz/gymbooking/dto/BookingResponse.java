package kz.gymbooking.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import kz.gymbooking.domain.BookingStatus;

public record BookingResponse(Long id, Long clientId, @JsonProperty("class") GymClassResponse gymClass,
                              BookingStatus status, Instant bookedAt, Instant cancelledAt, Instant checkedInAt) {
}
