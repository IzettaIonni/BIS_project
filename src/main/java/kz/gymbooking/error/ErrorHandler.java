package kz.gymbooking.error;

import java.util.List;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ErrorHandler {

    public static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemResponse> handleApi(ApiException e) {
        return problem(e.getStatus(), e.toProblem());
    }

    // MethodArgumentNotValidException extends BindException
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemResponse> handleBody(BindException e) {
        List<FieldErrorItem> errors = e.getFieldErrors().stream()
                .map(fe -> new FieldErrorItem(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return validation("Request failed validation", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemResponse> handleParams(HandlerMethodValidationException e) {
        List<FieldErrorItem> errors = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> new FieldErrorItem(r.getMethodParameter().getParameterName(),
                                err.getDefaultMessage())))
                .toList();
        return validation("Request failed validation", errors);
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ProblemResponse> handleType(TypeMismatchException e) {
        return validation("Request failed validation",
                List.of(new FieldErrorItem(e.getPropertyName(), "invalid value " + e.getValue())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return validation("Request body is invalid", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemResponse> handleDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, ApiException.forbidden().toProblem());
    }

    private ResponseEntity<ProblemResponse> validation(String title, List<FieldErrorItem> errors) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, new ProblemResponse(
                HttpStatus.UNPROCESSABLE_CONTENT.value(), ErrorCode.VALIDATION_FAILED.name(), title, null, errors));
    }

    private ResponseEntity<ProblemResponse> problem(HttpStatus status, ProblemResponse body) {
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(body);
    }
}
