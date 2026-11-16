package kz.gymbooking.dto;

import java.util.List;

public record TrainerResponse(Long id, Long userId, String fullName, List<String> specializations, String bio,
                              String photoUrl) {
}
