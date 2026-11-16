package kz.gymbooking.controller;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.BookingResponse;
import kz.gymbooking.dto.CancelClassRequest;
import kz.gymbooking.dto.GymClassCreate;
import kz.gymbooking.dto.GymClassResponse;
import kz.gymbooking.dto.GymClassUpdate;
import kz.gymbooking.dto.PageResponse;
import kz.gymbooking.repository.ClassFilter;
import kz.gymbooking.security.AuthUser;
import kz.gymbooking.service.ClassService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/classes")
public class ClassController {

    private final ClassService classes;

    public ClassController(ClassService classes) {
        this.classes = classes;
    }

    @GetMapping
    public PageResponse<GymClassResponse> list(@RequestParam(required = false) OffsetDateTime from,
                                               @RequestParam(required = false) OffsetDateTime to,
                                               @RequestParam(required = false) Long trainerId,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(defaultValue = "false") boolean onlyAvailable,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                                               @RequestParam(defaultValue = "0") @Min(0) int offset) {
        ClassFilter filter = new ClassFilter(from == null ? null : from.toInstant(),
                to == null ? null : to.toInstant(), trainerId, type, onlyAvailable);
        return classes.list(filter, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TRAINER')")
    public GymClassResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GymClassCreate req) {
        return classes.create(AuthUser.from(jwt), req);
    }

    @GetMapping("/{classId}")
    public GymClassResponse get(@PathVariable Long classId) {
        return classes.get(classId);
    }

    @PatchMapping("/{classId}")
    @PreAuthorize("hasAnyRole('ADMIN','TRAINER')")
    public GymClassResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long classId,
                                   @Valid @RequestBody GymClassUpdate req) {
        return classes.update(AuthUser.from(jwt), classId, req);
    }

    @PostMapping("/{classId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','TRAINER')")
    public GymClassResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long classId,
                                   @Valid @RequestBody(required = false) CancelClassRequest req) {
        return classes.cancel(AuthUser.from(jwt), classId, req);
    }

    @GetMapping("/{classId}/bookings")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','TRAINER')")
    public List<BookingResponse> bookings(@AuthenticationPrincipal Jwt jwt, @PathVariable Long classId) {
        return classes.listBookings(AuthUser.from(jwt), classId);
    }
}
