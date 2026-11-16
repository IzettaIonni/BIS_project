package kz.gymbooking.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public class JwtProperties {

    private String secret;
    private Duration accessTtl;
    private Duration refreshTtl;
    private Duration qrTtl;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public void setAccessTtl(Duration accessTtl) {
        this.accessTtl = accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    public void setRefreshTtl(Duration refreshTtl) {
        this.refreshTtl = refreshTtl;
    }

    public Duration getQrTtl() {
        return qrTtl;
    }

    public void setQrTtl(Duration qrTtl) {
        this.qrTtl = qrTtl;
    }
}
