package kz.gymbooking.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.random.RandomGenerator;

import kz.gymbooking.domain.DenyReason;
import kz.gymbooking.domain.User;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class TokenService {

    public record IssuedTokens(String accessToken, String refreshToken, long expiresIn) {
    }

    public record QrClaims(Long userId, String nonce, Instant expiresAt) {
    }

    private final JwtEncoder encoder;
    /** Verifies the signature only; expiry is judged against the Clock in {@link #parseQr}. */
    private final NimbusJwtDecoder qrDecoder;
    private final JwtProperties props;
    private final Clock clock;
    private final RandomGenerator random;

    public TokenService(JwtEncoder encoder, JwtProperties props, Clock clock, RandomGenerator random) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
        this.random = random;
        this.qrDecoder = NimbusJwtDecoder.withSecretKey(SecurityConfig.secretKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        this.qrDecoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
    }

    public IssuedTokens issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(props.getAccessTtl()))
                .claim("role", user.getRole().name())
                .claim("typ", "access")
                .build();
        String access = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedTokens(access, refresh, props.getAccessTtl().toSeconds());
    }

    public String issueQr(Long userId, String nonce, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .id(nonce)
                .issuedAt(clock.instant())
                .expiresAt(expiresAt)
                .claim("typ", "qr")
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public QrClaims parseQr(String token) {
        Jwt jwt;
        try {
            jwt = qrDecoder.decode(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new QrTokenException(DenyReason.TOKEN_INVALID);
        }
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new QrTokenException(DenyReason.TOKEN_INVALID);
        }
        String nonce = jwt.getId();
        Instant expiresAt = jwt.getExpiresAt();
        if (!"qr".equals(jwt.getClaimAsString("typ")) || nonce == null || expiresAt == null) {
            throw new QrTokenException(DenyReason.TOKEN_INVALID);
        }
        if (expiresAt.isBefore(clock.instant())) {
            throw new QrTokenException(DenyReason.TOKEN_EXPIRED);
        }
        return new QrClaims(userId, nonce, expiresAt);
    }

    public String hash(String rawRefresh) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawRefresh.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
