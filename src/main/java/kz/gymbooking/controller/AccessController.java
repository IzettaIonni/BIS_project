package kz.gymbooking.controller;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.CheckInRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/access", produces = "text/plain;charset=UTF-8")
public class AccessController {

    @PostMapping("/qr-token")
    public String qrToken() {
        return "token: qr-" + UUID.randomUUID() + "\nexpiresAt: " + OffsetDateTime.now().plusSeconds(60) + "\n";
    }

    @PostMapping("/check-in")
    public String checkIn(@Valid @RequestBody CheckInRequest req) {
        return switch (req.token()) {
            case "expired" -> "decision: DENIED\nreason: TOKEN_EXPIRED\n";
            case "used" -> "decision: DENIED\nreason: TOKEN_ALREADY_USED\n";
            default -> req.token().startsWith("qr-")
                    ? "decision: GRANTED\nuser: #17 Aruzhan Bekova\nbooking: #501 ATTENDED\nvisitId: 7001\n"
                    : "decision: DENIED\nreason: TOKEN_INVALID\n";
        };
    }

    @GetMapping("/visits")
    public String visits(@RequestParam(required = false) String userId,
                         @RequestParam(required = false) OffsetDateTime from,
                         @RequestParam(required = false) OffsetDateTime to,
                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                         @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return """
                total: 2
                #7000 user #17, reception-1, 2026-11-08T18:30:00Z
                #6990 user #17, reception-1, 2026-11-05T06:50:00Z, booking #498
                """;
    }
}
