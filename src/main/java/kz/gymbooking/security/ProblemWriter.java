package kz.gymbooking.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import kz.gymbooking.error.ProblemResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

final class ProblemWriter {

    private ProblemWriter() {
    }

    static void write(ObjectMapper mapper, HttpServletResponse response, ProblemResponse problem)
            throws IOException {
        response.setStatus(problem.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(problem));
    }
}
