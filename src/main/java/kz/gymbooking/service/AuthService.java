package kz.gymbooking.service;

import java.time.Clock;
import java.time.Instant;

import kz.gymbooking.domain.RefreshToken;
import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.User;
import kz.gymbooking.dto.LoginRequest;
import kz.gymbooking.dto.RefreshRequest;
import kz.gymbooking.dto.RegisterRequest;
import kz.gymbooking.dto.TokenPairResponse;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.error.ErrorCode;
import kz.gymbooking.repository.RefreshTokenRepository;
import kz.gymbooking.repository.UserRepository;
import kz.gymbooking.security.AuthUser;
import kz.gymbooking.security.JwtProperties;
import kz.gymbooking.security.TokenService;
import kz.gymbooking.security.TokenService.IssuedTokens;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Wrong email or password";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ResponseMapper mapper;
    private final JwtProperties props;
    private final Clock clock;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       TokenService tokenService, ResponseMapper mapper, JwtProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    public TokenPairResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict(ErrorCode.EMAIL_TAKEN, "Email is already registered");
        }
        User user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user.setRole(Role.CLIENT);
        user.setCreatedAt(clock.instant());
        return pair(users.save(user));
    }

    public TokenPairResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(AuthService::badCredentials);
        return pair(user);
    }

    public TokenPairResponse refresh(RefreshRequest req) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokens.findByTokenHash(tokenService.hash(req.refreshToken()))
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> unauthorized("Invalid refresh token"));
        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> unauthorized("Invalid refresh token"));
        stored.setRevoked(true);
        refreshTokens.save(stored);
        return pair(user);
    }

    public void logout(AuthUser auth) {
        refreshTokens.revokeAllByUserId(auth.id());
    }

    private TokenPairResponse pair(User user) {
        IssuedTokens tokens = tokenService.issue(user);
        RefreshToken refresh = new RefreshToken();
        refresh.setUserId(user.getId());
        refresh.setTokenHash(tokenService.hash(tokens.refreshToken()));
        refresh.setExpiresAt(clock.instant().plus(props.getRefreshTtl()));
        refresh.setRevoked(false);
        refreshTokens.save(refresh);
        return new TokenPairResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(),
                mapper.user(user));
    }

    private static ApiException badCredentials() {
        return unauthorized(BAD_CREDENTIALS);
    }

    private static ApiException unauthorized(String title) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, title);
    }
}
