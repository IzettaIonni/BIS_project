package kz.gymbooking.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.Device;
import kz.gymbooking.domain.DeviceStatus;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Metric;
import kz.gymbooking.domain.MetricType;
import kz.gymbooking.dto.ConnectDeviceRequest;
import kz.gymbooking.dto.DeviceProviderResponse;
import kz.gymbooking.dto.DeviceResponse;
import kz.gymbooking.dto.MetricResponse;
import kz.gymbooking.dto.SyncResultResponse;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.DeviceRepository;
import kz.gymbooking.repository.GymClassRepository;
import kz.gymbooking.repository.MetricRepository;
import kz.gymbooking.security.AuthUser;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    private static final String STUB = "stub";
    private static final String STUB_NAME = "Stub device (fake data)";

    private final DeviceRepository devices;
    private final MetricRepository metrics;
    private final BookingRepository bookings;
    private final GymClassRepository classes;
    private final StubMetricGenerator generator;
    private final Clock clock;

    public DeviceService(DeviceRepository devices, MetricRepository metrics, BookingRepository bookings,
                         GymClassRepository classes, StubMetricGenerator generator, Clock clock) {
        this.devices = devices;
        this.metrics = metrics;
        this.bookings = bookings;
        this.classes = classes;
        this.generator = generator;
        this.clock = clock;
    }

    public List<DeviceProviderResponse> providers() {
        return List.of(
                new DeviceProviderResponse(STUB, STUB_NAME, true),
                new DeviceProviderResponse("garmin", "Garmin Connect", false),
                new DeviceProviderResponse("fitbit", "Fitbit", false));
    }

    public List<DeviceResponse> listMine(AuthUser user) {
        return devices.findByUserIdAndStatusNot(user.id(), DeviceStatus.DISCONNECTED).stream()
                .map(DeviceService::toResponse).toList();
    }

    public DeviceResponse connect(AuthUser user, ConnectDeviceRequest req) {
        if (!STUB.equals(req.provider())) {
            throw ApiException.validation("provider", "Provider is unknown or not enabled");
        }
        Device d = new Device();
        d.setUserId(user.id());
        d.setProvider(STUB);
        d.setName(req.name() == null || req.name().isBlank() ? STUB_NAME : req.name());
        d.setStatus(DeviceStatus.CONNECTED);
        d.setConnectedAt(clock.instant());
        return toResponse(devices.save(d));
    }

    public void disconnect(AuthUser user, Long id) {
        Device d = own(user, id);
        d.setStatus(DeviceStatus.DISCONNECTED);
        devices.save(d);
    }

    public SyncResultResponse sync(AuthUser user, Long id) {
        Device d = own(user, id);
        if (d.getStatus() != DeviceStatus.CONNECTED) {
            throw ApiException.notFound("Device");
        }
        Instant now = clock.instant();
        Instant from = d.getLastSyncAt() != null ? d.getLastSyncAt() : now.minus(Duration.ofHours(24));
        List<Metric> generated = generator.generate(d, from, now);
        metrics.saveAll(generated);
        d.setLastSyncAt(now);
        devices.save(d);
        return new SyncResultResponse(generated.size(), now);
    }

    public List<MetricResponse> metrics(AuthUser user, MetricType type, Instant from, Instant to, Long bookingId) {
        if (bookingId != null) {
            Booking b = bookings.findById(bookingId)
                    .filter(x -> user.id().equals(x.getClientId()))
                    .orElseThrow(() -> ApiException.notFound("Booking"));
            GymClass c = classes.findById(b.getClassId()).orElseThrow(() -> ApiException.notFound("Class"));
            from = c.getStartTime();
            to = c.endTime();
        }
        return metrics.search(user.id(), type, from, to).stream()
                .map(m -> new MetricResponse(m.getId(), m.getDeviceId(), m.getType(), m.getValue(), m.getUnit(),
                        m.getRecordedAt()))
                .toList();
    }

    private Device own(AuthUser user, Long id) {
        return devices.findById(id).filter(d -> user.id().equals(d.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Device"));
    }

    private static DeviceResponse toResponse(Device d) {
        return new DeviceResponse(d.getId(), d.getProvider(), d.getName(), d.getStatus(), d.getConnectedAt(),
                d.getLastSyncAt());
    }
}
