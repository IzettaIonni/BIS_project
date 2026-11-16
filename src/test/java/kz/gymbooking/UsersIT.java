package kz.gymbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.User;
import kz.gymbooking.repository.Page;
import kz.gymbooking.support.Fixtures;
import kz.gymbooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

class UsersIT extends IntegrationTest {

    @Test
    void updateMeChangesNameAndPhone() throws Exception {
        when(users.findById(17L)).thenReturn(Optional.of(Fixtures.client()));

        mvc.perform(patch("/users/me").with(as(17, Role.CLIENT)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Aruzhan B.", "phone", "+77010000000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Aruzhan B."))
                .andExpect(jsonPath("$.phone").value("+77010000000"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getFullName()).isEqualTo("Aruzhan B.");
        assertThat(saved.getValue().getPhone()).isEqualTo("+77010000000");
    }

    @Test
    void adminListsUsers() throws Exception {
        when(users.search(Role.CLIENT, "aru", 20, 0)).thenReturn(new Page<>(List.of(Fixtures.client()), 1));

        mvc.perform(get("/users").param("role", "CLIENT").param("q", "aru").with(as(1, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].email").value("aruzhan@example.com"));
    }

    @Test
    void changeRoleRevokesTokens() throws Exception {
        when(users.findById(17L)).thenReturn(Optional.of(Fixtures.client()));

        mvc.perform(patch("/users/17/role").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "STAFF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"));
        verify(refreshTokens).revokeAllByUserId(17L);
    }

    @Test
    void changeRoleUnknownUserGives404() throws Exception {
        when(users.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(patch("/users/99/role").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "STAFF"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unknownRoleValueGives422() throws Exception {
        mvc.perform(patch("/users/17/role").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(patch("/users/17/role").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void clientCannotChangeRole() throws Exception {
        mvc.perform(patch("/users/17/role").with(as(17, Role.CLIENT)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ADMIN"))))
                .andExpect(status().isForbidden());
    }
}
