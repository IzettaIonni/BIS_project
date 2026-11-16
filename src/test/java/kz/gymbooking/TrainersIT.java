package kz.gymbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.Trainer;
import kz.gymbooking.domain.User;
import kz.gymbooking.repository.Page;
import kz.gymbooking.support.Fixtures;
import kz.gymbooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

class TrainersIT extends IntegrationTest {

    @Test
    void listIsPublic() throws Exception {
        when(trainers.search(null, 20, 0)).thenReturn(new Page<>(List.of(Fixtures.trainer()), 1));
        when(users.findById(3L)).thenReturn(Optional.of(Fixtures.trainerUser()));

        mvc.perform(get("/trainers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].fullName").value("Dana Serikova"));
    }

    @Test
    void adminCreatesTrainerAndPromotesUser() throws Exception {
        when(users.findById(17L)).thenReturn(Optional.of(Fixtures.client()));
        when(trainers.findByUserId(17L)).thenReturn(Optional.empty());

        mvc.perform(post("/trainers").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 17, "specializations", List.of("pilates"), "bio", "Hi"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(17))
                .andExpect(jsonPath("$.fullName").value("Aruzhan Bekova"))
                .andExpect(jsonPath("$.specializations[0]").value("pilates"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.TRAINER);
    }

    @Test
    void createForExistingTrainerGives409() throws Exception {
        when(users.findById(3L)).thenReturn(Optional.of(Fixtures.trainerUser()));
        when(trainers.findByUserId(3L)).thenReturn(Optional.of(Fixtures.trainer()));

        mvc.perform(post("/trainers").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 3))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_EXISTS"));
        verify(trainers, never()).save(any(Trainer.class));
    }

    @Test
    void createForUnknownUserGives404() throws Exception {
        when(users.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(post("/trainers").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 99))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void clientCannotCreateTrainer() throws Exception {
        mvc.perform(post("/trainers").with(as(17, Role.CLIENT)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 17))))
                .andExpect(status().isForbidden());
    }

    @Test
    void trainerUpdatesSelf() throws Exception {
        when(trainers.findById(3L)).thenReturn(Optional.of(Fixtures.trainer()));
        when(users.findById(3L)).thenReturn(Optional.of(Fixtures.trainerUser()));

        mvc.perform(patch("/trainers/3").with(as(3, Role.TRAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bio", "Updated bio", "specializations", List.of("yoga", "pilates")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Updated bio"))
                .andExpect(jsonPath("$.specializations[1]").value("pilates"));
    }

    @Test
    void otherTrainerUpdateGives403() throws Exception {
        when(trainers.findById(3L)).thenReturn(Optional.of(Fixtures.trainer()));

        mvc.perform(patch("/trainers/3").with(as(4, Role.TRAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bio", "Hacked"))))
                .andExpect(status().isForbidden());
        verify(trainers, never()).save(any(Trainer.class));
    }
}
