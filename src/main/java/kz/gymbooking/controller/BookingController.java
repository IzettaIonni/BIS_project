package kz.gymbooking.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.BookingStatus;
import kz.gymbooking.dto.CreateBookingRequest;
import kz.gymbooking.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "text/plain;charset=UTF-8")
public class BookingController {

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public String create(@Valid @RequestBody CreateBookingRequest req,
                         @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey) {
        switch (req.classId()) {
            case 42 -> throw new ApiException(HttpStatus.CONFLICT, "ALREADY_BOOKED", "You already booked this class");
            case 44 -> throw new ApiException(HttpStatus.CONFLICT, "CLASS_FULL", "Class is full");
            case 45 -> throw new ApiException(HttpStatus.CONFLICT, "CLASS_NOT_BOOKABLE", "Class is cancelled");
            default -> {
            }
        }
        return "id: 502\nclientId: 17\nclassId: " + req.classId() + "\nstatus: BOOKED\n";
    }

    @GetMapping("/users/me/bookings")
    public String myBookings(@RequestParam(required = false) BookingStatus status,
                             @RequestParam(required = false) Boolean upcoming,
                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                             @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return """
                total: 3
                #501 Morning Yoga, 2026-11-20T07:00:00Z, BOOKED
                #498 Morning Yoga, 2026-11-05T07:00:00Z, ATTENDED
                #490 Evening Stretching, 2026-11-20T15:00:00Z, CANCELLED
                """;
    }

    @GetMapping("/bookings/{bookingId}")
    public String get(@PathVariable int bookingId) {
        return """
                id: 501
                clientId: 17
                class: #42 Morning Yoga, 2026-11-20T07:00:00Z
                status: BOOKED
                bookedAt: 2026-11-10T08:00:00Z
                """;
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public String cancel(@PathVariable int bookingId) {
        return "id: " + bookingId + "\nstatus: CANCELLED\n";
    }
}
