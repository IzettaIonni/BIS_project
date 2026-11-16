package kz.gymbooking.repository;

import java.time.Instant;

public record ClassFilter(Instant from, Instant to, Long trainerId, String type, boolean onlyAvailable) {
}
