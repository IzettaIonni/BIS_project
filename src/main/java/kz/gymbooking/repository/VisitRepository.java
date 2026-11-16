package kz.gymbooking.repository;

import java.time.Instant;

import kz.gymbooking.domain.Visit;

public interface VisitRepository {

    Visit save(Visit visit);

    Page<Visit> search(Long userId, Instant from, Instant to, int limit, int offset);
}
