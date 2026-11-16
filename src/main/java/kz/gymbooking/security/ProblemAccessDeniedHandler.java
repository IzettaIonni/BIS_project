package kz.gymbooking.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.gymbooking.error.ErrorCode;
import kz.gymbooking.error.ProblemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper mapper;

    public ProblemAccessDeniedHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ProblemWriter.write(mapper, response, new ProblemResponse(HttpStatus.FORBIDDEN.value(),
                ErrorCode.FORBIDDEN.name(), "Access denied", null, null));
    }
}
