package kz.gymbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import kz.gymbooking.domain.RefreshToken;
import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.User;
import kz.gymbooking.security.TokenService;
import kz.gymbooking.support.Fixtures;
import kz.gymbooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.ResultActions;

class AuthIT extends IntegrationTest {

    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TokenService tokenService;
    @Autowired JwtEncoder jwtEncoder;

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private Map<String, String> registerBody(String email) {
        return Map.of("email", email, "password", "secret123", "fullName", "Aruzhan Bekova");
    }

    private User storedClient() {
        User u = Fixtures.client();
        u.setPasswordHash(passwordEncoder.encode("secret123"));
        return u;
    }

    @Test
    void registerCreatesClientAndReturnsTokens() throws Exception {
        when(users.existsByEmailIgnoreCase("aruzhan@example.com")).thenReturn(false);

        postJson("/auth/register", registerBody("aruzhan@example.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CLIENT"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").value(not(emptyString())))
                .andExpect(jsonPath("$.refreshToken").value(not(emptyString())));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CLIENT);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void registerTakenEmailGives409() throws Exception {
        when(users.existsByEmailIgnoreCase(anyString())).thenReturn(true);

        postJson("/auth/register", registerBody("aruzhan@example.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        verify(users, never()).save(any());
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        when(users.existsByEmailIgnoreCase("Aruzhan@Example.com")).thenReturn(false);
        postJson("/auth/register", registerBody("Aruzhan@Example.com")).andExpect(status().isCreated());
        verify(users).existsByEmailIgnoreCase("Aruzhan@Example.com");

        when(users.findByEmailIgnoreCase("Aruzhan@Example.com")).thenReturn(Optional.of(storedClient()));
        postJson("/auth/login", Map.of("email", "Aruzhan@Example.com", "password", "secret123"))
                .andExpect(status().isOk());
        verify(users).findByEmailIgnoreCase("Aruzhan@Example.com");
    }

    @Test
    void loginThenBearerWorks() throws Exception {
        User user = storedClient();
        when(users.findByEmailIgnoreCase("aruzhan@example.com")).thenReturn(Optional.of(user));
        when(users.findById(17L)).thenReturn(Optional.of(user));

        String body = postJson("/auth/login", Map.of("email", "aruzhan@example.com", "password", "secret123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        String access = objectMapper.readTree(body).get("accessToken").asString();

        mvc.perform(get("/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("aruzhan@example.com"));
    }

    @Test
    void expiredAccessTokenGives401() throws Exception {
        User user = storedClient();
        when(users.findByEmailIgnoreCase("aruzhan@example.com")).thenReturn(Optional.of(user));
        String body = postJson("/auth/login", Map.of("email", "aruzhan@example.com", "password", "secret123"))
                .andReturn().getResponse().getContentAsString();
        String access = objectMapper.readTree(body).get("accessToken").asString();

        clock.setInstant(NOW.plus(15, ChronoUnit.MINUTES).plusSeconds(1));
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWrongPasswordGives401() throws Exception {
        when(users.findByEmailIgnoreCase("aruzhan@example.com")).thenReturn(Optional.of(storedClient()));

        postJson("/auth/login", Map.of("email", "aruzhan@example.com", "password", "wrongpass"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.title").value("Wrong email or password"));
    }

    @Test
    void loginUnknownEmailGives401() throws Exception {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        postJson("/auth/login", Map.of("email", "nobody@example.com", "password", "secret123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.title").value("Wrong email or password"));
    }

    @Test
    void refreshRotates() throws Exception {
        RefreshToken old = Fixtures.refreshToken();
        when(refreshTokens.findByTokenHash(tokenService.hash("raw-token"))).thenReturn(Optional.of(old));
        when(users.findById(17L)).thenReturn(Optional.of(Fixtures.client()));

        postJson("/auth/refresh", Map.of("refreshToken", "raw-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(not(emptyString())))
                .andExpect(jsonPath("$.refreshToken").value(not("raw-token")))
                .andExpect(jsonPath("$.user.id").value(17));

        assertThat(old.isRevoked()).isTrue();
        verify(refreshTokens).save(old);
    }

    @Test
    void refreshRevokedOrExpiredGives401() throws Exception {
        RefreshToken revoked = Fixtures.refreshToken();
        revoked.setRevoked(true);
        when(refreshTokens.findByTokenHash(tokenService.hash("revoked"))).thenReturn(Optional.of(revoked));
        RefreshToken expired = Fixtures.refreshToken();
        expired.setExpiresAt(NOW.minusSeconds(1));
        when(refreshTokens.findByTokenHash(tokenService.hash("expired"))).thenReturn(Optional.of(expired));
        when(refreshTokens.findByTokenHash(tokenService.hash("unknown"))).thenReturn(Optional.empty());

        for (String raw : new String[] {"revoked", "expired", "unknown"}) {
            postJson("/auth/refresh", Map.of("refreshToken", raw))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void logoutRevokesAllRefreshTokens() throws Exception {
        mvc.perform(post("/auth/logout").with(as(17, Role.CLIENT)))
                .andExpect(status().isNoContent());
        verify(refreshTokens).revokeAllByUserId(17L);
    }

    @Test
    void tokenWithUnknownRoleGives401() throws Exception {
        for (String role : new String[] {"SUPERUSER", null}) {
            JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject("17").claim("typ", "access")
                    .issuedAt(NOW).expiresAt(NOW.plusSeconds(900));
            if (role != null) {
                claims.claim("role", role);
            }
            String token = jwtEncoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
            mvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }
}
