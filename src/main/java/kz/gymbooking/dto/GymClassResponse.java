package kz.gymbooking.dto;

import java.time.Instant;

import kz.gymbooking.domain.ClassStatus;

public record GymClassResponse(Long id, String name, String type, TrainerRef trainer, Instant startTime,
                               int durationMin, String room, int capacity, long bookedCount, ClassStatus status) {
}
