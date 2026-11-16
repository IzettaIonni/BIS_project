package kz.gymbooking.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemResponse(int status, String code, String title, String detail, List<FieldErrorItem> errors) {
}
