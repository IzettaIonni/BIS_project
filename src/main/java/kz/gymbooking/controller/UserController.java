package kz.gymbooking.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.domain.Role;
import kz.gymbooking.dto.PageResponse;
import kz.gymbooking.dto.RoleChangeRequest;
import kz.gymbooking.dto.UserResponse;
import kz.gymbooking.dto.UserUpdate;
import kz.gymbooking.security.AuthUser;
import kz.gymbooking.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.getMe(AuthUser.from(jwt));
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UserUpdate req) {
        return users.updateMe(AuthUser.from(jwt), req);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> list(@RequestParam(required = false) Role role,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                                           @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return users.list(role, q, limit, offset);
    }

    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse changeRole(@PathVariable Long userId, @Valid @RequestBody RoleChangeRequest req) {
        return users.changeRole(userId, req.role());
    }
}
