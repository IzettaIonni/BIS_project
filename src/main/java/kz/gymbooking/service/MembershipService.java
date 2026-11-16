package kz.gymbooking.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import kz.gymbooking.domain.Membership;
import kz.gymbooking.domain.MembershipPlan;
import kz.gymbooking.domain.MembershipStatus;
import kz.gymbooking.dto.BuyMembershipRequest;
import kz.gymbooking.dto.MembershipPlanResponse;
import kz.gymbooking.dto.MembershipResponse;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.error.ErrorCode;
import kz.gymbooking.repository.MembershipPlanRepository;
import kz.gymbooking.repository.MembershipRepository;
import kz.gymbooking.security.AuthUser;
import org.springframework.stereotype.Service;

@Service
public class MembershipService {

    private final MembershipPlanRepository plans;
    private final MembershipRepository memberships;
    private final ResponseMapper mapper;
    private final Clock clock;
    private final ZoneId gymZone;

    public MembershipService(MembershipPlanRepository plans, MembershipRepository memberships,
                             ResponseMapper mapper, Clock clock, ZoneId gymZone) {
        this.plans = plans;
        this.memberships = memberships;
        this.mapper = mapper;
        this.clock = clock;
        this.gymZone = gymZone;
    }

    public List<MembershipPlanResponse> listPlans() {
        return plans.findAll().stream().map(mapper::plan).toList();
    }

    public MembershipResponse buy(AuthUser auth, BuyMembershipRequest req) {
        MembershipPlan plan = plans.findById(req.planId()).orElseThrow(() -> ApiException.notFound("Plan"));
        LocalDate today = today();
        LocalDate start = req.startDate() == null ? today : req.startDate();
        if (start.isBefore(today)) {
            throw ApiException.validation("startDate", "startDate must not be in the past");
        }
        LocalDate end = start.plusDays(plan.getDurationDays() - 1L);
        boolean overlaps = memberships.findByClientIdAndStatusIn(auth.id(),
                        Set.of(MembershipStatus.ACTIVE, MembershipStatus.FROZEN)).stream()
                .anyMatch(m -> !m.getStartDate().isAfter(end) && !m.getEndDate().isBefore(start));
        if (overlaps) {
            throw ApiException.conflict(ErrorCode.MEMBERSHIP_OVERLAP, "You already have an active membership");
        }
        Membership m = new Membership();
        m.setClientId(auth.id());
        m.setPlanId(plan.getId());
        m.setStartDate(start);
        m.setEndDate(end);
        m.setVisitsLeft(plan.getVisitLimit());
        m.setStatus(MembershipStatus.ACTIVE);
        return mapper.membership(memberships.save(m));
    }

    public List<MembershipResponse> listMine(AuthUser auth) {
        return memberships.findByClientId(auth.id()).stream().map(mapper::membership).toList();
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(gymZone));
    }

    public List<Membership> findCovering(Long clientId, LocalDate date) {
        return memberships.findByClientIdAndStatusIn(clientId, Set.of(MembershipStatus.ACTIVE)).stream()
                .filter(m -> m.coversDate(date))
                .sorted(Comparator.comparing(Membership::getEndDate))
                .toList();
    }

    public Optional<Membership> findUsable(Long clientId, LocalDate date) {
        return findCovering(clientId, date).stream().filter(Membership::hasVisitsLeft).findFirst();
    }
}
