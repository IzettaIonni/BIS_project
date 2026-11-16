package kz.gymbooking.repository;

import kz.gymbooking.domain.UsedQrNonce;

public interface UsedQrNonceRepository {

    boolean existsByNonce(String nonce);

    UsedQrNonce save(UsedQrNonce nonce);
}
