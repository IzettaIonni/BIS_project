package kz.gymbooking.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.PageResponse;
import kz.gymbooking.dto.TrainerCreate;
import kz.gymbooking.dto.TrainerResponse;
import kz.gymbooking.dto.TrainerUpdate;
import kz.gymbooking.security.AuthUser;
import kz.gymbooking.service.TrainerService;
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
@RequestMapping("/trainers")
public class TrainerController {

    private final TrainerService trainers;

    public TrainerController(TrainerService trainers) {
        this.trainers = trainers;
    }

    @GetMapping
    public PageResponse<TrainerResponse> list(@RequestParam(required = false) String specialization,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                                              @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return trainers.list(specialization, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TrainerResponse create(@Valid @RequestBody TrainerCreate req) {
        return trainers.create(req);
    }

    @GetMapping("/{trainerId}")
    public TrainerResponse get(@PathVariable Long trainerId) {
        return trainers.get(trainerId);
    }

    @PatchMapping("/{trainerId}")
    public TrainerResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long trainerId,
                                  @Valid @RequestBody TrainerUpdate req) {
        return trainers.update(AuthUser.from(jwt), trainerId, req);
    }
}
