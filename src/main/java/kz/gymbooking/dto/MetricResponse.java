package kz.gymbooking.dto;

import java.time.Instant;

import kz.gymbooking.domain.MetricType;

public record MetricResponse(Long id, Long deviceId, MetricType type, double value, String unit, Instant recordedAt) {
}
