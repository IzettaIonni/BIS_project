package kz.gymbooking.config;

import java.time.Clock;
import java.time.ZoneId;
import java.security.SecureRandom;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ZoneId gymZone(@Value("${app.time-zone}") String zone) {
        return ZoneId.of(zone);
    }

    // Must stay a SecureRandom in production: refresh tokens and QR nonces are drawn from it.
    @Bean
    public RandomGenerator randomGenerator() {
        return new SecureRandom();
    }
}
