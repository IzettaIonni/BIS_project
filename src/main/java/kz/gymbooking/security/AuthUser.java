package kz.gymbooking.security;

import kz.gymbooking.domain.Role;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthUser(Long id, Role role) {

    public static AuthUser from(Jwt jwt) {
        return new AuthUser(Long.valueOf(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString("role")));
    }
}
