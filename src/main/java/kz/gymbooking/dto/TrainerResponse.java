package kz.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrainerResponse(Long id, Long userId, String fullName, List<String> specializations, String bio,
                              String photoUrl) {
}
