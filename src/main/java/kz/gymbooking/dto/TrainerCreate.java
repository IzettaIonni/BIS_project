package kz.gymbooking.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record TrainerCreate(@NotNull Long userId, List<String> specializations, String bio) {
}
