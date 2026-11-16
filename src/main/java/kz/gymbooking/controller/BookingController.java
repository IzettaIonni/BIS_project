package kz.gymbooking.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.domain.BookingStatus;
import kz.gymbooking.dto.BookingResponse;
import kz.gymbooking.dto.CreateBookingRequest;
import kz.gymbooking.dto.PageResponse;
import kz.gymbooking.security.AuthUser;
import kz.gymbooking.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public BookingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBookingRequest req,
                                  @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey) {
        return bookings.create(AuthUser.from(jwt), req, idempotencyKey);
    }

    @GetMapping("/users/me/bookings")
    public PageResponse<BookingResponse> myBookings(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestParam(required = false) BookingStatus status,
                                                    @RequestParam(required = false) Boolean upcoming,
                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                                                    @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return bookings.listMine(AuthUser.from(jwt), status, upcoming, limit, offset);
    }

    @GetMapping("/bookings/{bookingId}")
    public BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return bookings.get(AuthUser.from(jwt), bookingId);
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
        return bookings.cancel(AuthUser.from(jwt), bookingId);
    }
}
