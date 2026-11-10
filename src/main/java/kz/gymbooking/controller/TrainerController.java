package kz.gymbooking.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.TrainerCreate;
import kz.gymbooking.dto.TrainerUpdate;
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
@RequestMapping(value = "/trainers", produces = "text/plain;charset=UTF-8")
public class TrainerController {

    @GetMapping
    public String list(@RequestParam(required = false) String specialization,
                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                       @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return """
                total: 2
                #3 Dana Serikova, yoga, stretching
                #4 Timur Akhmetov, crossfit, hiit
                """;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String create(@Valid @RequestBody TrainerCreate req) {
        return "id: 10\nuserId: " + req.userId() + "\nspecializations: " + req.specializations() + "\n";
    }

    @GetMapping("/{trainerId}")
    public String get(@PathVariable int trainerId) {
        return """
                id: 3
                userId: 3
                fullName: Dana Serikova
                specializations: yoga, stretching
                bio: Yoga teacher, 6 years
                """;
    }

    @PatchMapping("/{trainerId}")
    public String update(@PathVariable int trainerId, @Valid @RequestBody TrainerUpdate req) {
        return "id: " + trainerId + "\nspecializations: " + req.specializations() + "\nbio: " + req.bio() + "\n";
    }
}
