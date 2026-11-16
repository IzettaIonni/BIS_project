package kz.gymbooking.repository;

import java.util.Optional;

import kz.gymbooking.domain.RefreshToken;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void revokeAllByUserId(Long userId);
}
