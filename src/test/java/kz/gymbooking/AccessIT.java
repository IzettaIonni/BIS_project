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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.BookingStatus;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Membership;
import kz.gymbooking.domain.MembershipStatus;
import kz.gymbooking.domain.Role;
import kz.gymbooking.repository.Page;
import kz.gymbooking.support.Fixtures;
import kz.gymbooking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class AccessIT extends IntegrationTest {

    private Membership limited;

    @BeforeEach
    void stub() {
        limited = Fixtures.membership();
        limited.setPlanId(2L);
        limited.setVisitsLeft(12);
        when(memberships.findByClientIdAndStatusIn(eq(17L), any())).thenReturn(List.of(limited));
        when(plans.findById(1L)).thenReturn(Optional.of(Fixtures.monthlyUnlimited()));
        when(plans.findById(2L)).thenReturn(Optional.of(Fixtures.twelveVisits()));
        when(users.findById(17L)).thenReturn(Optional.of(Fixtures.client()));
        when(trainers.findById(3L)).thenReturn(Optional.of(Fixtures.trainer()));
        when(users.findById(3L)).thenReturn(Optional.of(Fixtures.trainerUser()));
    }

    private String qr() throws Exception {
        String body = mvc.perform(post("/access/qr-token").with(as(17, Role.CLIENT)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private ResultActions checkIn(String token) throws Exception {
        return mvc.perform(post("/access/check-in").with(as(5, Role.STAFF)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token, "gate", "reception-1"))));
    }

    private Booking bookingAt(java.time.Instant start) {
        GymClass c = Fixtures.gymClass();
        c.setStartTime(start);
        when(classes.findById(42L)).thenReturn(Optional.of(c));
        when(bookings.countActiveByClassId(any())).thenReturn(1L);
        Booking b = Fixtures.booking();
        when(bookings.findBookedByClientWithClassStartBetween(eq(17L), eq(NOW.minus(Duration.ofMinutes(30))),
                eq(NOW.plus(Duration.ofMinutes(30))))).thenAnswer(i ->
                start.isBefore(NOW.plus(Duration.ofMinutes(31))) ? List.of(b) : List.of());
        return b;
    }

    @Test
    void qrIssuedForMember() throws Exception {
        mvc.perform(post("/access/qr-token").with(as(17, Role.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").value("2026-11-16T10:01:00Z"));
    }

    @Test
    void qrWithoutMembershipGives403() throws Exception {
        when(memberships.findByClientIdAndStatusIn(eq(17L), any())).thenReturn(List.of());
        mvc.perform(post("/access/qr-token").with(as(17, Role.CLIENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_MEMBERSHIP"));
    }

    @Test
    void checkInGrantedRoundTrip() throws Exception {
        bookingAt(NOW.plus(Duration.ofMinutes(20)));
        checkIn(qr())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("GRANTED"))
                .andExpect(jsonPath("$.visitId").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(17))
                .andExpect(jsonPath("$.membership.visitsLeft").value(11))
                .andExpect(jsonPath("$.booking.status").value("ATTENDED"));
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        org.junit.jupiter.api.Assertions.assertEquals(BookingStatus.ATTENDED, saved.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(NOW, saved.getValue().getCheckedInAt());
        verify(usedNonces).markUsed(any(), any());
        verify(visits).save(any());
    }

    @Test
    void bookingOutsideWindowUntouched() throws Exception {
        bookingAt(NOW.plus(Duration.ofMinutes(40)));
        checkIn(qr())
                .andExpect(jsonPath("$.decision").value("GRANTED"))
                .andExpect(jsonPath("$.booking").doesNotHaveJsonPath());
        verify(bookings, never()).save(any());
    }

    @Test
    void lostNonceRaceDenied() throws Exception {
        String token = qr();
        when(usedNonces.markUsed(any(), any())).thenReturn(false);
        checkIn(token)
                .andExpect(jsonPath("$.decision").value("DENIED"))
                .andExpect(jsonPath("$.reason").value("TOKEN_ALREADY_USED"))
                .andExpect(jsonPath("$.user").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.membership").doesNotHaveJsonPath());
        verify(visits, never()).save(any());
        verify(memberships, never()).save(any());
        verify(bookings, never()).save(any());
    }

    @Test
    void unlimitedMembershipGrantedWithoutDecrement() throws Exception {
        Membership unlimited = Fixtures.membership();
        when(memberships.findByClientIdAndStatusIn(eq(17L), any())).thenReturn(List.of(unlimited));
        checkIn(qr())
                .andExpect(jsonPath("$.decision").value("GRANTED"))
                .andExpect(jsonPath("$.membership.status").value("ACTIVE"))
                .andExpect(jsonPath("$.membership.visitsLeft").doesNotExist());
        verify(memberships, never()).save(any());
        org.junit.jupiter.api.Assertions.assertNull(unlimited.getVisitsLeft());
        org.junit.jupiter.api.Assertions.assertEquals(MembershipStatus.ACTIVE, unlimited.getStatus());
    }

    @Test
    void staffNonNumericUserIdGives422() throws Exception {
        for (Role role : new Role[] {Role.STAFF, Role.ADMIN}) {
            mvc.perform(get("/access/visits").param("userId", "abc").with(as(5, role)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("userId"));
        }
        mvc.perform(get("/access/visits").param("userId", "abc").with(as(17, Role.CLIENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastVisitExpiresMembership() throws Exception {
        limited.setVisitsLeft(1);
        checkIn(qr()).andExpect(jsonPath("$.decision").value("GRANTED"));
        ArgumentCaptor<Membership> saved = ArgumentCaptor.forClass(Membership.class);
        verify(memberships).save(saved.capture());
        org.junit.jupiter.api.Assertions.assertEquals(0, saved.getValue().getVisitsLeft());
        org.junit.jupiter.api.Assertions.assertEquals(MembershipStatus.EXPIRED, saved.getValue().getStatus());
    }

    @Test
    void garbageTokenIsInvalid() throws Exception {
        checkIn("garbage").andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENIED"))
                .andExpect(jsonPath("$.reason").value("TOKEN_INVALID"));
    }

    @Test
    void accessTokenScannedAsQrIsInvalid() throws Exception {
        String access = accessToken();
        checkIn(access).andExpect(jsonPath("$.reason").value("TOKEN_INVALID"));
    }

    @Autowired
    private kz.gymbooking.security.TokenService tokenService;

    private String accessToken() {
        return tokenService.issue(Fixtures.client()).accessToken();
    }

    @Test
    void expiredQr() throws Exception {
        String token = qr();
        clock.advance(Duration.ofSeconds(61));
        checkIn(token).andExpect(jsonPath("$.decision").value("DENIED"))
                .andExpect(jsonPath("$.reason").value("TOKEN_EXPIRED"));
    }

    @Test
    void reusedNonceDenied() throws Exception {
        when(usedNonces.existsByNonce(any())).thenReturn(true);
        checkIn(qr()).andExpect(jsonPath("$.reason").value("TOKEN_ALREADY_USED"));
        verify(visits, never()).save(any());
    }

    @Test
    void noVisitsLeftDenied() throws Exception {
        String token = qr();
        limited.setVisitsLeft(0);
        checkIn(token).andExpect(jsonPath("$.reason").value("NO_VISITS_LEFT"));
    }

    @Test
    void qrTokenAsBearerGives401() throws Exception {
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + qr()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientCannotCheckIn() throws Exception {
        mvc.perform(post("/access/check-in").with(as(17, Role.CLIENT)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "x"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientSeesOwnVisits() throws Exception {
        when(visits.search(eq(17L), any(), any(), eq(20), eq(0)))
                .thenReturn(new Page<>(List.of(Fixtures.visit()), 1));
        mvc.perform(get("/access/visits").param("userId", "me").with(as(17, Role.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(17));
        verify(visits).search(eq(17L), any(), any(), eq(20), eq(0));
    }

    @Test
    void clientOtherUserVisitsGives403() throws Exception {
        mvc.perform(get("/access/visits").param("userId", "18").with(as(17, Role.CLIENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/access/visits").with(as(17, Role.CLIENT))).andExpect(status().isForbidden());
    }

    @Test
    void staffSeesAllVisits() throws Exception {
        when(visits.search(eq(null), any(), any(), eq(20), eq(0)))
                .thenReturn(new Page<>(List.of(Fixtures.visit()), 1));
        mvc.perform(get("/access/visits").with(as(5, Role.STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        verify(visits).search(eq(null), any(), any(), eq(20), eq(0));
    }
}
