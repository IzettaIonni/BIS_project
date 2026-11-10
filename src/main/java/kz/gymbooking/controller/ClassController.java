package kz.gymbooking.controller;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.CancelClassRequest;
import kz.gymbooking.dto.GymClassCreate;
import kz.gymbooking.dto.GymClassUpdate;
import kz.gymbooking.error.ApiException;
import org.springframework.http.HttpStatus;
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
@RequestMapping(value = "/classes", produces = "text/plain;charset=UTF-8")
public class ClassController {

    @GetMapping
    public String list(@RequestParam(required = false) OffsetDateTime from,
                       @RequestParam(required = false) OffsetDateTime to,
                       @RequestParam(required = false) Integer trainerId,
                       @RequestParam(required = false) String type,
                       @RequestParam(defaultValue = "false") boolean onlyAvailable,
                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                       @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return """
                total: 4
                #42 Morning Yoga, 2026-11-20T07:00:00Z, Hall A, trainer #3, 12/15, SCHEDULED
                #43 CrossFit Basics, 2026-11-20T13:00:00Z, Hall B, trainer #4, 8/20, SCHEDULED
                #44 Evening Stretching, 2026-11-20T15:00:00Z, Hall A, trainer #3, 10/10, SCHEDULED
                #45 HIIT Express, 2026-11-20T12:00:00Z, Hall B, trainer #4, 0/12, CANCELLED
                """;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String create(@Valid @RequestBody GymClassCreate req) {
        return "id: 100\nname: " + req.name() + "\nstartTime: " + req.startTime()
                + "\nroom: " + req.room() + "\ncapacity: " + req.capacity() + "\nstatus: SCHEDULED\n";
    }

    @GetMapping("/{classId}")
    public String get(@PathVariable int classId) {
        if (classId < 42 || classId > 45) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Class not found");
        }
        return """
                id: 42
                name: Morning Yoga
                type: yoga
                trainer: #3 Dana Serikova
                startTime: 2026-11-20T07:00:00Z
                durationMin: 60
                room: Hall A
                capacity: 15
                bookedCount: 12
                status: SCHEDULED
                """;
    }

    @PatchMapping("/{classId}")
    public String update(@PathVariable int classId, @Valid @RequestBody GymClassUpdate req) {
        return "id: " + classId + "\nupdated\n";
    }

    @PostMapping("/{classId}/cancel")
    public String cancel(@PathVariable int classId, @Valid @RequestBody(required = false) CancelClassRequest req) {
        return "id: " + classId + "\nstatus: CANCELLED\n";
    }

    @GetMapping("/{classId}/bookings")
    public String bookings(@PathVariable int classId) {
        return """
                #501 client #17, BOOKED
                #503 client #21, BOOKED
                """;
    }
}
