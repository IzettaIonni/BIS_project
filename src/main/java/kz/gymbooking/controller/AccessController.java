package kz.gymbooking.controller;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.CheckInRequest;
import kz.gymbooking.dto.CheckInResponse;
import kz.gymbooking.dto.PageResponse;
import kz.gymbooking.dto.QrTokenResponse;
import kz.gymbooking.dto.VisitResponse;
import kz.gymbooking.security.AuthUser;
import kz.gymbooking.service.AccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/access")
public class AccessController {

    private final AccessService access;

    public AccessController(AccessService access) {
        this.access = access;
    }

    @PostMapping("/qr-token")
    @PreAuthorize("hasRole('CLIENT')")
    public QrTokenResponse qrToken(@AuthenticationPrincipal Jwt jwt) {
        return access.issueQr(AuthUser.from(jwt));
    }

    @PostMapping("/check-in")
    @PreAuthorize("hasRole('STAFF')")
    public CheckInResponse checkIn(@Valid @RequestBody CheckInRequest req) {
        return access.checkIn(req);
    }

    @GetMapping("/visits")
    public PageResponse<VisitResponse> visits(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) String userId,
                                              @RequestParam(required = false) Instant from,
                                              @RequestParam(required = false) Instant to,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                                              @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return access.listVisits(AuthUser.from(jwt), userId, from, to, limit, offset);
    }
}
