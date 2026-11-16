package kz.gymbooking.repository;

import java.time.Instant;

public interface UsedQrNonceRepository {

    boolean existsByNonce(String nonce);

    /**
     * Atomically records the nonce; returns false if it was already present.
     * TODO(db): implement with a unique key on nonce (insert, treat duplicate as false).
     */
    boolean markUsed(String nonce, Instant expiresAt);
}
