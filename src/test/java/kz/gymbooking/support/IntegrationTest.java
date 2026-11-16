package kz.gymbooking.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import java.util.SplittableRandom;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.Device;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Membership;
import kz.gymbooking.domain.Metric;
import kz.gymbooking.domain.RefreshToken;
import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.Trainer;
import kz.gymbooking.domain.User;
import kz.gymbooking.domain.Visit;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.DeviceRepository;
import kz.gymbooking.repository.GymClassRepository;
import kz.gymbooking.repository.MembershipPlanRepository;
import kz.gymbooking.repository.MembershipRepository;
import kz.gymbooking.repository.MetricRepository;
import kz.gymbooking.repository.RefreshTokenRepository;
import kz.gymbooking.repository.TrainerRepository;
import kz.gymbooking.repository.UsedQrNonceRepository;
import kz.gymbooking.repository.UserRepository;
import kz.gymbooking.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.jobs.enabled=false")
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    public static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    @TestConfiguration
    static class TestClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }

        @Bean
        @Primary
        RandomGenerator testRandom() {
            return new SplittableRandom(42);
        }
    }

    @MockitoBean protected UserRepository users;
    @MockitoBean protected TrainerRepository trainers;
    @MockitoBean protected GymClassRepository classes;
    @MockitoBean protected BookingRepository bookings;
    @MockitoBean protected MembershipPlanRepository plans;
    @MockitoBean protected MembershipRepository memberships;
    @MockitoBean protected VisitRepository visits;
    @MockitoBean protected DeviceRepository devices;
    @MockitoBean protected MetricRepository metrics;
    @MockitoBean protected RefreshTokenRepository refreshTokens;
    @MockitoBean protected UsedQrNonceRepository usedNonces;

    @Autowired protected WebApplicationContext ctx;
    @Autowired protected MutableClock clock;
    @Autowired protected ObjectMapper objectMapper;

    protected MockMvc mvc;

    private final AtomicLong ids = new AtomicLong(1000);

    @BeforeEach
    void resetSupport() {
        clock.setInstant(NOW);
        mvc = MockMvcBuilders.webAppContextSetup(ctx)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(users.save(any(User.class))).thenAnswer(i -> withId(i.getArgument(0)));
        when(trainers.save(any(Trainer.class))).thenAnswer(i -> withId(i.getArgument(0)));
        when(classes.save(any(GymClass.class))).thenAnswer(i -> withId(i.getArgument(0)));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> withId(i.getArgument(0)));
        when(memberships.save(any(Membership.class))).thenAnswer(i -> withId(i.getArgument(0)));
        when(visits.save(any(Visit.class))).thenAnswer(i -> withId(i.getArgument(0)));
        when(devices.save(any(Device.class))).thenAnswer(i -> withId(i.getArgument(0)));
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(i -> withId(i.getArgument(0)));
        when(usedNonces.markUsed(any(), any())).thenReturn(true);
        when(refreshTokens.revokeIfActive(any())).thenReturn(true);
        when(metrics.saveAll(any())).thenAnswer(i -> {
            List<Metric> list = i.getArgument(0);
            list.forEach(this::withId);
            return list;
        });
    }

    private <T> T withId(T entity) {
        try {
            Method getter = entity.getClass().getMethod("getId");
            if (getter.invoke(entity) == null) {
                entity.getClass().getMethod("setId", Long.class).invoke(entity, ids.incrementAndGet());
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return entity;
    }

    protected RequestPostProcessor as(long userId, Role role) {
        return jwt()
                .jwt(j -> j.subject(String.valueOf(userId)).claim("role", role.name()).claim("typ", "access"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    protected String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

}
