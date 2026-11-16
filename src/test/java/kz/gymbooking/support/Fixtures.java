package kz.gymbooking.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.BookingStatus;
import kz.gymbooking.domain.ClassStatus;
import kz.gymbooking.domain.Device;
import kz.gymbooking.domain.DeviceStatus;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Membership;
import kz.gymbooking.domain.MembershipPlan;
import kz.gymbooking.domain.MembershipStatus;
import kz.gymbooking.domain.Metric;
import kz.gymbooking.domain.MetricType;
import kz.gymbooking.domain.RefreshToken;
import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.Trainer;
import kz.gymbooking.domain.UsedQrNonce;
import kz.gymbooking.domain.User;
import kz.gymbooking.domain.Visit;

public final class Fixtures {

    private Fixtures() {
    }

    public static User client() {
        User u = new User();
        u.setId(17L);
        u.setEmail("aruzhan@example.com");
        u.setPasswordHash("{hash}");
        u.setFullName("Aruzhan Bekova");
        u.setPhone("+77011234567");
        u.setRole(Role.CLIENT);
        u.setCreatedAt(IntegrationTest.NOW.minus(30, ChronoUnit.DAYS));
        return u;
    }

    public static User trainerUser() {
        User u = new User();
        u.setId(3L);
        u.setEmail("dana.trainer@example.com");
        u.setPasswordHash("{hash}");
        u.setFullName("Dana Serikova");
        u.setRole(Role.TRAINER);
        u.setCreatedAt(IntegrationTest.NOW.minus(60, ChronoUnit.DAYS));
        return u;
    }

    public static Trainer trainer() {
        Trainer t = new Trainer();
        t.setId(3L);
        t.setUserId(3L);
        t.setSpecializations(List.of("yoga"));
        t.setBio("Yoga instructor");
        return t;
    }

    public static GymClass gymClass() {
        GymClass c = new GymClass();
        c.setId(42L);
        c.setName("Morning Yoga");
        c.setType("yoga");
        c.setTrainerId(3L);
        c.setStartTime(IntegrationTest.NOW.plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
                .plus(7, ChronoUnit.HOURS));
        c.setDurationMin(60);
        c.setRoom("Hall A");
        c.setCapacity(15);
        c.setStatus(ClassStatus.SCHEDULED);
        return c;
    }

    public static Booking booking() {
        Booking b = new Booking();
        b.setId(1L);
        b.setClientId(17L);
        b.setClassId(42L);
        b.setStatus(BookingStatus.BOOKED);
        b.setBookedAt(IntegrationTest.NOW);
        b.setIdempotencyKey(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        return b;
    }

    public static MembershipPlan monthlyUnlimited() {
        MembershipPlan p = new MembershipPlan();
        p.setId(1L);
        p.setName("Monthly Unlimited");
        p.setDurationDays(30);
        p.setVisitLimit(null);
        p.setPrice(25000);
        return p;
    }

    public static MembershipPlan twelveVisits() {
        MembershipPlan p = new MembershipPlan();
        p.setId(2L);
        p.setName("12 Visits");
        p.setDurationDays(60);
        p.setVisitLimit(12);
        p.setPrice(18000);
        return p;
    }

    public static Membership membership() {
        Membership m = new Membership();
        m.setId(1L);
        m.setClientId(17L);
        m.setPlanId(1L);
        LocalDate today = LocalDate.ofInstant(IntegrationTest.NOW, java.time.ZoneOffset.UTC);
        m.setStartDate(today.minusDays(5));
        m.setEndDate(today.plusDays(25));
        m.setVisitsLeft(null);
        m.setStatus(MembershipStatus.ACTIVE);
        return m;
    }

    public static Visit visit() {
        Visit v = new Visit();
        v.setId(1L);
        v.setUserId(17L);
        v.setGate("main");
        v.setCheckedInAt(IntegrationTest.NOW);
        return v;
    }

    public static Device device() {
        Device d = new Device();
        d.setId(1L);
        d.setUserId(17L);
        d.setProvider("apple-health");
        d.setName("Apple Watch");
        d.setStatus(DeviceStatus.CONNECTED);
        d.setConnectedAt(IntegrationTest.NOW);
        return d;
    }

    public static Metric metric() {
        Metric m = new Metric();
        m.setId(1L);
        m.setUserId(17L);
        m.setDeviceId(1L);
        m.setType(MetricType.HEART_RATE);
        m.setValue(72);
        m.setUnit("bpm");
        m.setRecordedAt(IntegrationTest.NOW);
        return m;
    }

    public static RefreshToken refreshToken() {
        RefreshToken t = new RefreshToken();
        t.setId(1L);
        t.setUserId(17L);
        t.setTokenHash("hash");
        t.setExpiresAt(IntegrationTest.NOW.plus(30, ChronoUnit.DAYS));
        return t;
    }

    public static UsedQrNonce usedQrNonce() {
        UsedQrNonce n = new UsedQrNonce();
        n.setNonce("nonce");
        n.setExpiresAt(Instant.parse("2026-11-16T10:01:00Z"));
        return n;
    }
}
