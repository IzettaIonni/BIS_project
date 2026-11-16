package kz.gymbooking.domain;

import java.time.Instant;

public class UsedQrNonce {

    private String nonce;
    private Instant expiresAt;

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
