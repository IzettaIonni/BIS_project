package kz.gymbooking.controller;

import jakarta.validation.Valid;
import kz.gymbooking.dto.BuyMembershipRequest;
import kz.gymbooking.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "text/plain;charset=UTF-8")
public class MembershipController {

    @GetMapping("/membership-plans")
    public String plans() {
        return """
                #1 Monthly Unlimited, 30 days, unlimited, 25000 KZT
                #2 12 Visits, 60 days, 12 visits, 18000 KZT
                #3 Annual Unlimited, 365 days, unlimited, 220000 KZT
                """;
    }

    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    public String buy(@Valid @RequestBody BuyMembershipRequest req) {
        if (req.startDate() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMBERSHIP_OVERLAP", "You already have an active membership");
        }
        return "id: 901\nplanId: " + req.planId() + "\nstartDate: " + req.startDate() + "\nstatus: ACTIVE\n";
    }

    @GetMapping("/users/me/memberships")
    public String myMemberships() {
        return """
                #900 Monthly Unlimited, 2026-10-31 - 2026-11-30, ACTIVE
                #850 12 Visits, 2026-08-22 - 2026-10-21, EXPIRED
                """;
    }
}
