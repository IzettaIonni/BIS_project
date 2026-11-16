package kz.gymbooking;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import kz.gymbooking.domain.Role;
import kz.gymbooking.repository.ClassFilter;
import kz.gymbooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class FoundationIT extends IntegrationTest {

    @Test
    void unexpectedExceptionGives500Problem() throws Exception {
        when(classes.search(any(ClassFilter.class), eq(20), eq(0))).thenThrow(new IllegalStateException("boom"));
        mvc.perform(get("/classes"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.title").value("Internal server error"));
    }

    @Test
    void unknownPathGives404Problem() throws Exception {
        mvc.perform(get("/nope").with(as(17, Role.CLIENT)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodGives405Problem() throws Exception {
        mvc.perform(delete("/classes/42").with(as(17, Role.CLIENT)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rootIsPublic() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void missingTokenGives401Problem() throws Exception {
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void garbageBearerGives401() throws Exception {
        mvc.perform(get("/users/me").header("Authorization", "Bearer abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientOnAdminEndpointGives403Problem() throws Exception {
        mvc.perform(get("/users").with(as(17, Role.CLIENT)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invalidBodyGives422WithFieldErrors() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("email", "password", "fullName")));
    }
}
