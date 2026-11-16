package kz.gymbooking.dto;

import java.time.Instant;

import kz.gymbooking.domain.DeviceStatus;

public record DeviceResponse(Long id, String provider, String name, DeviceStatus status, Instant connectedAt,
                             Instant lastSyncAt) {
}
