package kz.gymbooking.error;

import java.util.List;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final List<FieldErrorItem> errors;

    public ApiException(HttpStatus status, ErrorCode code, String title) {
        this(status, code, title, null);
    }

    public ApiException(HttpStatus status, ErrorCode code, String title, List<FieldErrorItem> errors) {
        super(title);
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, what + " not found");
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Access denied");
    }

    public static ApiException conflict(ErrorCode code, String title) {
        return new ApiException(HttpStatus.CONFLICT, code, title);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.VALIDATION_FAILED,
                "Request failed validation", List.of(new FieldErrorItem(field, message)));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<FieldErrorItem> getErrors() {
        return errors;
    }

    public ProblemResponse toProblem() {
        return new ProblemResponse(status.value(), code.name(), getMessage(), null, errors);
    }
}
