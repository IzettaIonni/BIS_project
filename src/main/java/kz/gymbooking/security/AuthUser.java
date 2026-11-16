package kz.gymbooking.security;

import kz.gymbooking.domain.Role;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthUser(Long id, Role role) {

    public static AuthUser from(Jwt jwt) {
        return new AuthUser(Long.valueOf(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString("role")));
    }

    /** True for an access token whose subject and role claims can be turned into an {@link AuthUser}. */
    static boolean isValidAccessToken(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("typ"))) {
            return false;
        }
        try {
            from(jwt);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
