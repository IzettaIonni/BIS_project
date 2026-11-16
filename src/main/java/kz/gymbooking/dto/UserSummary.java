package kz.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserSummary(Long id, String fullName, String photoUrl) {
}
