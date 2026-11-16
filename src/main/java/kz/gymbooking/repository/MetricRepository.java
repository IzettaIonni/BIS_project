package kz.gymbooking.repository;

import java.time.Instant;
import java.util.List;

import kz.gymbooking.domain.Metric;
import kz.gymbooking.domain.MetricType;

public interface MetricRepository {

    List<Metric> saveAll(List<Metric> metrics);

    List<Metric> search(Long userId, MetricType type, Instant from, Instant to);
}
