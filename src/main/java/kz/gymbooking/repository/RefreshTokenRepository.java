package kz.gymbooking.repository;

import java.util.Optional;

import kz.gymbooking.domain.RefreshToken;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Sets revoked only if the token is currently not revoked; returns whether a row changed.
     * TODO(db): implement as a conditional UPDATE ... WHERE id = ? AND revoked = false.
     */
    boolean revokeIfActive(Long id);

    void revokeAllByUserId(Long userId);
}
