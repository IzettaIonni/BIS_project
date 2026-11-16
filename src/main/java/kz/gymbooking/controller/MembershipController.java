package kz.gymbooking.controller;

import java.util.List;

import jakarta.validation.Valid;
import kz.gymbooking.dto.BuyMembershipRequest;
import kz.gymbooking.dto.MembershipPlanResponse;
import kz.gymbooking.dto.MembershipResponse;
import kz.gymbooking.security.AuthUser;
import kz.gymbooking.service.MembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MembershipController {

    private final MembershipService memberships;

    public MembershipController(MembershipService memberships) {
        this.memberships = memberships;
    }

    @GetMapping("/membership-plans")
    public List<MembershipPlanResponse> plans() {
        return memberships.listPlans();
    }

    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipResponse buy(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BuyMembershipRequest req) {
        return memberships.buy(AuthUser.from(jwt), req);
    }

    @GetMapping("/users/me/memberships")
    public List<MembershipResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return memberships.listMine(AuthUser.from(jwt));
    }
}
