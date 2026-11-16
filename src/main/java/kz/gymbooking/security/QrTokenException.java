package kz.gymbooking.security;

import kz.gymbooking.domain.DenyReason;

public class QrTokenException extends RuntimeException {

    private final DenyReason reason;

    public QrTokenException(DenyReason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public DenyReason getReason() {
        return reason;
    }
}
