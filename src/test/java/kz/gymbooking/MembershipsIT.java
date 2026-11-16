package kz.gymbooking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import kz.gymbooking.domain.Membership;
import kz.gymbooking.domain.MembershipPlan;
import kz.gymbooking.domain.MembershipStatus;
import kz.gymbooking.domain.Role;
import kz.gymbooking.support.Fixtures;
import kz.gymbooking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class MembershipsIT extends IntegrationTest {

    @BeforeEach
    void stubPlans() {
        MembershipPlan annual = new MembershipPlan();
        annual.setId(3L);
        annual.setName("Annual Unlimited");
        annual.setDurationDays(365);
        annual.setPrice(220000);
        when(plans.findAll()).thenReturn(List.of(Fixtures.monthlyUnlimited(), Fixtures.twelveVisits(), annual));
        when(plans.findById(1L)).thenReturn(Optional.of(Fixtures.monthlyUnlimited()));
        when(plans.findById(2L)).thenReturn(Optional.of(Fixtures.twelveVisits()));
        when(memberships.findByClientIdAndStatusIn(any(), any())).thenReturn(List.of());
    }

    private org.springframework.test.web.servlet.ResultActions buy(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/memberships").with(as(17, Role.CLIENT)).contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    @Test
    void plansArePublic() throws Exception {
        mvc.perform(get("/membership-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].visitLimit").doesNotExist())
                .andExpect(jsonPath("$[1].visitLimit").value(12));
    }

    @Test
    void buyDefaultsToToday() throws Exception {
        buy(Map.of("planId", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value("2026-11-16"))
                .andExpect(jsonPath("$.endDate").value("2026-12-15"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.clientId").value(17));
    }

    @Test
    void buyLimitedPlanSetsVisits() throws Exception {
        buy(Map.of("planId", 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitsLeft").value(12));
    }

    @Test
    void todayUsesGymZone() throws Exception {
        clock.setInstant(Instant.parse("2026-11-16T20:00:00Z"));
        buy(Map.of("planId", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value("2026-11-17"));
    }

    @Test
    void overlapWithActiveGives409() throws Exception {
        when(memberships.findByClientIdAndStatusIn(any(), any())).thenReturn(List.of(Fixtures.membership()));
        buy(Map.of("planId", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_OVERLAP"));
    }

    @Test
    void expiredMembershipDoesNotBlock() throws Exception {
        Membership expired = Fixtures.membership();
        expired.setStatus(MembershipStatus.EXPIRED);
        // repository filters by status, so an expired-only history yields nothing
        when(memberships.findByClientId(17L)).thenReturn(List.of(expired));
        buy(Map.of("planId", 1)).andExpect(status().isCreated());
        verify(memberships).findByClientIdAndStatusIn(eq(17L),
                argThat(s -> s.size() == 2 && s.containsAll(Set.of(MembershipStatus.ACTIVE, MembershipStatus.FROZEN))));
    }

    @Test
    void frozenOverlapGives409() throws Exception {
        Membership frozen = Fixtures.membership();
        frozen.setStatus(MembershipStatus.FROZEN);
        when(memberships.findByClientIdAndStatusIn(eq(17L), any())).thenReturn(List.of(frozen));
        buy(Map.of("planId", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_OVERLAP"));
    }

    @Test
    void pastStartDateGives422() throws Exception {
        buy(Map.of("planId", 1, "startDate", "2026-11-15"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("startDate"));
    }

    @Test
    void unknownPlanGives404() throws Exception {
        buy(Map.of("planId", 99)).andExpect(status().isNotFound());
    }

    @Test
    void listMineReturnsHistory() throws Exception {
        Membership old = Fixtures.membership();
        old.setId(2L);
        old.setStatus(MembershipStatus.EXPIRED);
        when(memberships.findByClientId(17L)).thenReturn(List.of(Fixtures.membership(), old));
        when(plans.findById(1L)).thenReturn(Optional.of(Fixtures.monthlyUnlimited()));
        mvc.perform(get("/users/me/memberships").with(as(17, Role.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].plan.name").value("Monthly Unlimited"))
                .andExpect(jsonPath("$[1].status").value("EXPIRED"));
    }
}
