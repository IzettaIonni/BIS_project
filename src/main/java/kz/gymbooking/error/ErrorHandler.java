package kz.gymbooking.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemResponse> handleNoResource(NoResourceFoundException e) {
        return problem(HttpStatus.NOT_FOUND, ApiException.notFound("Resource").toProblem());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, new ProblemResponse(HttpStatus.METHOD_NOT_ALLOWED.value(),
                ErrorCode.VALIDATION_FAILED.name(), "Method not allowed", null, null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, new ProblemResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), ErrorCode.VALIDATION_FAILED.name(),
                "Unsupported media type", null, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, new ProblemResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.INTERNAL_ERROR.name(),
                "Internal server error", null, null));
    }

    private ResponseEntity<ProblemResponse> validation(String title, List<FieldErrorItem> errors) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, new ProblemResponse(
                HttpStatus.UNPROCESSABLE_CONTENT.value(), ErrorCode.VALIDATION_FAILED.name(), title, null, errors));
    }

    private ResponseEntity<ProblemResponse> problem(HttpStatus status, ProblemResponse body) {
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(body);
    }
}
