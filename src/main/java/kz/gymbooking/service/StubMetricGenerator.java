package kz.gymbooking.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import kz.gymbooking.domain.Device;
import kz.gymbooking.domain.Metric;
import kz.gymbooking.domain.MetricType;
import org.springframework.stereotype.Component;

/** Produces fake hourly readings for the stub device provider. */
@Component
public class StubMetricGenerator {

    private final RandomGenerator random;

    public StubMetricGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** One heart-rate, steps and calories reading per full hour in the half-open interval (from, to]. */
    public List<Metric> generate(Device device, Instant from, Instant to) {
        List<Metric> result = new ArrayList<>();
        Instant hour = from.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS);
        for (; !hour.isAfter(to); hour = hour.plus(1, ChronoUnit.HOURS)) {
            result.add(metric(device, MetricType.HEART_RATE, random.nextInt(60, 161), "bpm", hour));
            result.add(metric(device, MetricType.STEPS, random.nextInt(0, 1501), "steps", hour));
            result.add(metric(device, MetricType.CALORIES, random.nextInt(20, 401), "kcal", hour));
        }
        return result;
    }

    private Metric metric(Device device, MetricType type, double value, String unit, Instant at) {
        Metric m = new Metric();
        m.setUserId(device.getUserId());
        m.setDeviceId(device.getId());
        m.setType(type);
        m.setValue(value);
        m.setUnit(unit);
        m.setRecordedAt(at);
        return m;
    }
}
