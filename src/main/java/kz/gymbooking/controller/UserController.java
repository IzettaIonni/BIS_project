package kz.gymbooking.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.Role;
import kz.gymbooking.dto.RoleChangeRequest;
import kz.gymbooking.dto.UserUpdate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/users", produces = "text/plain;charset=UTF-8")
public class UserController {

    @GetMapping("/me")
    public String me() {
        return """
                id: 17
                email: aruzhan@example.com
                fullName: Aruzhan Bekova
                phone: +77011234567
                role: CLIENT
                createdAt: 2026-10-11T09:00:00Z
                """;
    }

    @PatchMapping("/me")
    public String updateMe(@Valid @RequestBody UserUpdate req) {
        return "id: 17\nfullName: " + req.fullName() + "\nphone: " + req.phone() + "\nrole: CLIENT\n";
    }

    @GetMapping
    public String list(@RequestParam(required = false) Role role,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                       @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return """
                total: 4
                #1 Admin, admin@example.com, ADMIN
                #3 Dana Serikova, dana.trainer@example.com, TRAINER
                #5 Reception Desk, reception@example.com, STAFF
                #17 Aruzhan Bekova, aruzhan@example.com, CLIENT
                """;
    }

    @PatchMapping("/{userId}/role")
    public String changeRole(@PathVariable int userId, @Valid @RequestBody RoleChangeRequest req) {
        return "id: " + userId + "\nrole: " + req.role() + "\n";
    }
}
