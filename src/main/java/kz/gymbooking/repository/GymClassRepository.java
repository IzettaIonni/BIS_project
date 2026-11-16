package kz.gymbooking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import kz.gymbooking.domain.GymClass;

public interface GymClassRepository {

    Optional<GymClass> findById(Long id);

    Optional<GymClass> findByIdForUpdate(Long id);

    GymClass save(GymClass gymClass);

    Page<GymClass> search(ClassFilter filter, int limit, int offset);

    /** SCHEDULED classes sharing the trainer or the room whose interval intersects [start, end). */
    List<GymClass> findOverlapping(Long trainerId, String room, Instant start, Instant end, Long excludeClassId);

    List<GymClass> findScheduledEndedBefore(Instant instant);
}
