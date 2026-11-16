package kz.gymbooking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.BookingStatus;

public interface BookingRepository {

    Optional<Booking> findById(Long id);

    Booking save(Booking booking);

    /** Counts bookings in status BOOKED or ATTENDED. */
    long countActiveByClassId(Long classId);

    Optional<Booking> findActiveByClientIdAndClassId(Long clientId, Long classId);

    Optional<Booking> findByClientIdAndIdempotencyKey(Long clientId, UUID idempotencyKey);

    List<Booking> findByClassId(Long classId);

    List<Booking> findByClassIdAndStatus(Long classId, BookingStatus status);

    Page<Booking> searchByClient(Long clientId, BookingStatus status, Boolean upcoming, Instant now,
                                 int limit, int offset);

    List<Booking> findBookedByClientWithClassStartBetween(Long clientId, Instant from, Instant to);
}
