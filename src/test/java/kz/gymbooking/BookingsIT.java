package kz.gymbooking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.BookingStatus;
import kz.gymbooking.domain.ClassStatus;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Membership;
import kz.gymbooking.domain.Role;
import kz.gymbooking.repository.Page;
import kz.gymbooking.support.Fixtures;
import kz.gymbooking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class BookingsIT extends IntegrationTest {

    private GymClass cls;

    @BeforeEach
    void stub() {
        cls = Fixtures.gymClass();
        stubClass(cls);
        when(memberships.findByClientIdAndStatusIn(any(), any())).thenReturn(List.of(Fixtures.membership()));
        when(bookings.findActiveByClientIdAndClassId(any(), any())).thenReturn(Optional.empty());
        when(bookings.countActiveByClassId(any())).thenReturn(0L);
        when(trainers.findByUserId(3L)).thenReturn(Optional.of(Fixtures.trainer()));
        when(trainers.findById(3L)).thenReturn(Optional.of(Fixtures.trainer()));
        when(users.findById(3L)).thenReturn(Optional.of(Fixtures.trainerUser()));
    }

    private void stubClass(GymClass c) {
        when(classes.findById(c.getId())).thenReturn(Optional.of(c));
        when(classes.findByIdForUpdate(c.getId())).thenReturn(Optional.of(c));
    }

    private ResultActions book(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/bookings").with(as(17, Role.CLIENT)).contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private Booking stored() {
        Booking b = Fixtures.booking();
        when(bookings.findById(1L)).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void bookingSucceeds() throws Exception {
        book(Map.of("classId", 42))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.class.id").value(42));
    }

    @Test
    void idempotentRepeatReturnsSameBooking() throws Exception {
        UUID key = UUID.randomUUID();
        when(bookings.findByClientIdAndIdempotencyKey(17L, key)).thenReturn(Optional.of(Fixtures.booking()));
        mvc.perform(post("/bookings").with(as(17, Role.CLIENT)).header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("classId", 42))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
        verify(bookings, never()).save(any());
    }

    @Test
    void unknownClassGives404() throws Exception {
        book(Map.of("classId", 99)).andExpect(status().isNotFound());
    }

    @Test
    void cancelledClassNotBookable() throws Exception {
        cls.setStatus(ClassStatus.CANCELLED);
        book(Map.of("classId", 42)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_BOOKABLE"));
    }

    @Test
    void classStartingNowNotBookable() throws Exception {
        cls.setStartTime(NOW);
        book(Map.of("classId", 42)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_BOOKABLE"));
    }

    @Test
    void noMembershipGives403() throws Exception {
        when(memberships.findByClientIdAndStatusIn(any(), any())).thenReturn(List.of());
        book(Map.of("classId", 42)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_MEMBERSHIP"));
    }

    @Test
    void membershipEndingBeforeClassDateGives403() throws Exception {
        Membership m = Fixtures.membership();
        m.setEndDate(LocalDate.ofInstant(NOW, ZoneId.of("Asia/Almaty")));
        when(memberships.findByClientIdAndStatusIn(any(), any())).thenReturn(List.of(m));
        book(Map.of("classId", 42)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_MEMBERSHIP"));
    }

    @Test
    void alreadyBookedGives409() throws Exception {
        when(bookings.findActiveByClientIdAndClassId(17L, 42L)).thenReturn(Optional.of(Fixtures.booking()));
        book(Map.of("classId", 42)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_BOOKED"));
    }

    @Test
    void fullClassGives409() throws Exception {
        when(bookings.countActiveByClassId(42L)).thenReturn(15L);
        book(Map.of("classId", 42)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASS_FULL"));
    }

    @Test
    void ruleOrderMembershipBeforeFull() throws Exception {
        when(bookings.countActiveByClassId(42L)).thenReturn(15L);
        when(memberships.findByClientIdAndStatusIn(any(), any())).thenReturn(List.of());
        book(Map.of("classId", 42)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_MEMBERSHIP"));
    }

    @Test
    void trainerCannotBook() throws Exception {
        mvc.perform(post("/bookings").with(as(3, Role.TRAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("classId", 42))))
                .andExpect(status().isForbidden());
    }

    private ResultActions cancel(long userId, Role role) throws Exception {
        return mvc.perform(post("/bookings/1/cancel").with(as(userId, role)));
    }

    @Test
    void ownerCancelsInTime() throws Exception {
        stored();
        cls.setStartTime(NOW.plus(Duration.ofHours(3)));
        cancel(17, Role.CLIENT).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelExactlyTwoHoursBeforeAllowed() throws Exception {
        stored();
        cls.setStartTime(NOW.plus(Duration.ofHours(2)));
        cancel(17, Role.CLIENT).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelInsideTwoHoursGives409() throws Exception {
        stored();
        cls.setStartTime(NOW.plus(Duration.ofHours(1)));
        cancel(17, Role.CLIENT).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_DEADLINE_PASSED"));
    }

    @Test
    void adminCancelsAnytime() throws Exception {
        stored();
        cls.setStartTime(NOW.plus(Duration.ofMinutes(10)));
        cancel(1, Role.ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOthersBookingGives403() throws Exception {
        stored();
        cancel(18, Role.CLIENT).andExpect(status().isForbidden());
    }

    @Test
    void cancelTwiceGives409() throws Exception {
        Booking b = stored();
        b.setStatus(BookingStatus.CANCELLED);
        cancel(17, Role.CLIENT).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void otherClientCannotView() throws Exception {
        stored();
        mvc.perform(get("/bookings/1").with(as(18, Role.CLIENT))).andExpect(status().isForbidden());
    }

    @Test
    void classTrainerCanView() throws Exception {
        stored();
        mvc.perform(get("/bookings/1").with(as(3, Role.TRAINER))).andExpect(status().isOk());
    }

    @Test
    void listMinePassesFilters() throws Exception {
        when(bookings.searchByClient(17L, BookingStatus.BOOKED, true, NOW, 20, 0))
                .thenReturn(new Page<>(List.of(Fixtures.booking()), 1));
        mvc.perform(get("/users/me/bookings").param("status", "BOOKED").param("upcoming", "true")
                        .with(as(17, Role.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(1));
        verify(bookings).searchByClient(eq(17L), eq(BookingStatus.BOOKED), eq(true), eq(NOW), eq(20), eq(0));
    }
}
