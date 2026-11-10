package kz.gymbooking.error;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<String> handleApi(ApiException e) {
        return text(e.getStatus(), e.getCode(), e.getMessage(), List.of());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<String> handleBody(BindException e) {
        List<String> errors = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return validation(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<String> handleParams(HandlerMethodValidationException e) {
        List<String> errors = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> r.getMethodParameter().getParameterName() + ": " + err.getDefaultMessage()))
                .toList();
        return validation(errors);
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<String> handleType(TypeMismatchException e) {
        return validation(List.of(e.getPropertyName() + ": invalid value " + e.getValue()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleUnreadable(HttpMessageNotReadableException e) {
        return text(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Request body is invalid", List.of());
    }

    private ResponseEntity<String> validation(List<String> errors) {
        return text(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Request failed validation", errors);
    }

    private ResponseEntity<String> text(HttpStatus status, String code, String title, List<String> errors) {
        StringBuilder body = new StringBuilder()
                .append("status: ").append(status.value()).append('\n')
                .append("code: ").append(code).append('\n')
                .append("title: ").append(title).append('\n');
        for (String error : errors) {
            body.append("  - ").append(error).append('\n');
        }
        return ResponseEntity.status(status)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(body.toString());
    }
}
