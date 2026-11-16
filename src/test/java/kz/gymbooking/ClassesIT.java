package kz.gymbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.BookingStatus;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Role;
import kz.gymbooking.repository.ClassFilter;
import kz.gymbooking.repository.Page;
import kz.gymbooking.support.Fixtures;
import kz.gymbooking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

class ClassesIT extends IntegrationTest {

    @BeforeEach
    void stubTrainer() {
        when(trainers.findById(3L)).thenReturn(Optional.of(Fixtures.trainer()));
        when(trainers.findByUserId(3L)).thenReturn(Optional.of(Fixtures.trainer()));
        when(users.findById(3L)).thenReturn(Optional.of(Fixtures.trainerUser()));
        when(classes.findOverlapping(anyLong(), any(), any(), any(), any())).thenReturn(List.of());
    }

    private Map<String, Object> createBody(long trainerId, String start) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Evening Yoga");
        body.put("type", "yoga");
        body.put("trainerId", trainerId);
        body.put("startTime", start);
        body.put("durationMin", 60);
        body.put("room", "Hall A");
        body.put("capacity", 10);
        return body;
    }

    @Test
    void listIsPublicWithBookedCount() throws Exception {
        when(classes.search(any(ClassFilter.class), eq(20), eq(0)))
                .thenReturn(new Page<>(List.of(Fixtures.gymClass()), 1));
        when(bookings.countActiveByClassId(42L)).thenReturn(12L);

        mvc.perform(get("/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].bookedCount").value(12))
                .andExpect(jsonPath("$.items[0].trainer.fullName").value("Dana Serikova"))
                .andExpect(jsonPath("$.items[0].status").value("SCHEDULED"));
    }

    @Test
    void limitOver100Gives422() throws Exception {
        mvc.perform(get("/classes").param("limit", "101"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void adminCreatesClass() throws Exception {
        mvc.perform(post("/classes").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(createBody(3, "2026-11-20T18:00:00Z"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.name").value("Evening Yoga"))
                .andExpect(jsonPath("$.trainer.id").value(3));
    }

    @Test
    void trainerCreatesOwnClass() throws Exception {
        mvc.perform(post("/classes").with(as(3, Role.TRAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(createBody(3, "2026-11-20T18:00:00Z"))))
                .andExpect(status().isCreated());
    }

    @Test
    void trainerCreatingForOtherTrainerGives403() throws Exception {
        mvc.perform(post("/classes").with(as(3, Role.TRAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(createBody(4, "2026-11-20T18:00:00Z"))))
                .andExpect(status().isForbidden());
        verify(classes, never()).save(any(GymClass.class));
    }

    @Test
    void unknownTrainerGives422() throws Exception {
        when(trainers.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(post("/classes").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(createBody(99, "2026-11-20T18:00:00Z"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("trainerId"));
    }

    @Test
    void overlapGives409ScheduleConflict() throws Exception {
        when(classes.findOverlapping(eq(3L), eq("Hall A"), any(), any(), any()))
                .thenReturn(List.of(Fixtures.gymClass()));

        mvc.perform(post("/classes").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(createBody(3, "2026-11-20T18:00:00Z"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
    }

    @Test
    void startInPastGives422() throws Exception {
        mvc.perform(post("/classes").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(createBody(3, "2026-11-16T09:00:00Z"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("startTime"));
    }

    @Test
    void updateChangesNameAndCapacity() throws Exception {
        when(classes.findById(42L)).thenReturn(Optional.of(Fixtures.gymClass()));
        when(bookings.countActiveByClassId(42L)).thenReturn(5L);

        mvc.perform(patch("/classes/42").with(as(3, Role.TRAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Sunrise Yoga", "capacity", 8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sunrise Yoga"))
                .andExpect(jsonPath("$.capacity").value(8));
    }

    @Test
    void capacityBelowBookedGives409() throws Exception {
        when(classes.findById(42L)).thenReturn(Optional.of(Fixtures.gymClass()));
        when(bookings.countActiveByClassId(42L)).thenReturn(12L);

        mvc.perform(patch("/classes/42").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("capacity", 10))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void trainerChangingTrainerIdGives403() throws Exception {
        when(classes.findById(42L)).thenReturn(Optional.of(Fixtures.gymClass()));

        mvc.perform(patch("/classes/42").with(as(3, Role.TRAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("trainerId", 4))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCancelledClassGives409() throws Exception {
        GymClass cancelled = Fixtures.gymClass();
        cancelled.setStatus(kz.gymbooking.domain.ClassStatus.CANCELLED);
        when(classes.findById(42L)).thenReturn(Optional.of(cancelled));

        mvc.perform(patch("/classes/42").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "X"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void movingStartIntoPastGives422() throws Exception {
        when(classes.findById(42L)).thenReturn(Optional.of(Fixtures.gymClass()));

        mvc.perform(patch("/classes/42").with(as(1, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("startTime", "2026-11-15T09:00:00Z"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("startTime"));
    }

    @Test
    void cancelCascadesBookings() throws Exception {
        when(classes.findById(42L)).thenReturn(Optional.of(Fixtures.gymClass()));
        Booking b1 = Fixtures.booking();
        Booking b2 = Fixtures.booking();
        b2.setId(2L);
        b2.setClientId(21L);
        when(bookings.findByClassIdAndStatus(42L, BookingStatus.BOOKED)).thenReturn(List.of(b1, b2));

        mvc.perform(post("/classes/42/cancel").with(as(3, Role.TRAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Instructor ill"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(b -> {
            assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(b.getCancelledAt()).isEqualTo(NOW);
        });
        ArgumentCaptor<GymClass> savedClass = ArgumentCaptor.forClass(GymClass.class);
        verify(classes).save(savedClass.capture());
        assertThat(savedClass.getValue().getCancelReason()).isEqualTo("Instructor ill");
    }

    @Test
    void cancelWithoutBodyWorks() throws Exception {
        when(classes.findById(42L)).thenReturn(Optional.of(Fixtures.gymClass()));
        when(bookings.findByClassIdAndStatus(42L, BookingStatus.BOOKED)).thenReturn(List.of());

        mvc.perform(post("/classes/42/cancel").with(as(1, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void demotedTrainerCannotCancel() throws Exception {
        when(classes.findById(42L)).thenReturn(Optional.of(Fixtures.gymClass()));

        mvc.perform(post("/classes/42/cancel").with(as(3, Role.CLIENT)))
                .andExpect(status().isForbidden());
        verify(classes, never()).save(any(GymClass.class));
    }

    @Test
    void staffSeesAttendance() throws Exception {
        when(classes.findById(42L)).thenReturn(Optional.of(Fixtures.gymClass()));
        when(bookings.findByClassId(42L)).thenReturn(List.of(Fixtures.booking()));

        mvc.perform(get("/classes/42/bookings").with(as(5, Role.STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId").value(17))
                .andExpect(jsonPath("$[0].class.id").value(42));
    }

    @Test
    void clientCannotSeeAttendance() throws Exception {
        mvc.perform(get("/classes/42/bookings").with(as(17, Role.CLIENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherTrainerCannotSeeAttendance() throws Exception {
        when(classes.findById(42L)).thenReturn(Optional.of(Fixtures.gymClass()));
        when(trainers.findByUserId(4L)).thenReturn(Optional.of(otherTrainer()));

        mvc.perform(get("/classes/42/bookings").with(as(4, Role.TRAINER)))
                .andExpect(status().isForbidden());
    }

    private kz.gymbooking.domain.Trainer otherTrainer() {
        kz.gymbooking.domain.Trainer t = Fixtures.trainer();
        t.setId(4L);
        t.setUserId(4L);
        return t;
    }

    @Test
    void unknownClassGives404() throws Exception {
        when(classes.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/classes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
