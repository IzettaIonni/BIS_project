package kz.gymbooking.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.BookingStatus;
import kz.gymbooking.domain.ClassStatus;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.Trainer;
import kz.gymbooking.dto.BookingResponse;
import kz.gymbooking.dto.CancelClassRequest;
import kz.gymbooking.dto.GymClassCreate;
import kz.gymbooking.dto.GymClassResponse;
import kz.gymbooking.dto.GymClassUpdate;
import kz.gymbooking.dto.PageResponse;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.error.ErrorCode;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.ClassFilter;
import kz.gymbooking.repository.GymClassRepository;
import kz.gymbooking.repository.TrainerRepository;
import kz.gymbooking.security.AuthUser;
import org.springframework.stereotype.Service;

@Service
public class ClassService {

    private final GymClassRepository classes;
    private final TrainerRepository trainers;
    private final BookingRepository bookings;
    private final ResponseMapper mapper;
    private final Permissions permissions;
    private final Clock clock;

    public ClassService(GymClassRepository classes, TrainerRepository trainers, BookingRepository bookings,
                        ResponseMapper mapper, Permissions permissions, Clock clock) {
        this.classes = classes;
        this.trainers = trainers;
        this.bookings = bookings;
        this.mapper = mapper;
        this.permissions = permissions;
        this.clock = clock;
    }

    public PageResponse<GymClassResponse> list(ClassFilter filter, int limit, int offset) {
        return PageResponse.of(classes.search(filter, limit, offset), mapper::gymClass);
    }

    public GymClassResponse get(Long id) {
        return mapper.gymClass(load(id));
    }

    public GymClassResponse create(AuthUser auth, GymClassCreate req) {
        if (auth.role() == Role.TRAINER) {
            Long ownId = trainers.findByUserId(auth.id()).map(Trainer::getId).orElse(null);
            if (ownId == null || !ownId.equals(req.trainerId())) {
                throw ApiException.forbidden();
            }
        }
        requireTrainer(req.trainerId());
        Instant start = req.startTime().toInstant();
        requireFuture(start);

        GymClass c = new GymClass();
        c.setName(req.name());
        c.setType(req.type());
        c.setTrainerId(req.trainerId());
        c.setStartTime(start);
        c.setDurationMin(req.durationMin());
        c.setRoom(req.room());
        c.setCapacity(req.capacity());
        c.setStatus(ClassStatus.SCHEDULED);
        requireNoOverlap(c, null);
        return mapper.gymClass(classes.save(c));
    }

    public GymClassResponse update(AuthUser auth, Long id, GymClassUpdate req) {
        GymClass c = load(id);
        requireManager(auth, c);
        requireScheduled(c);

        if (req.trainerId() != null && !req.trainerId().equals(c.getTrainerId())) {
            if (auth.role() != Role.ADMIN) {
                throw ApiException.forbidden();
            }
            requireTrainer(req.trainerId());
            c.setTrainerId(req.trainerId());
        }
        if (req.startTime() != null) {
            Instant start = req.startTime().toInstant();
            requireFuture(start);
            c.setStartTime(start);
        }
        if (req.name() != null) {
            c.setName(req.name());
        }
        if (req.durationMin() != null) {
            c.setDurationMin(req.durationMin());
        }
        if (req.room() != null) {
            c.setRoom(req.room());
        }
        if (req.capacity() != null) {
            if (req.capacity() < bookings.countActiveByClassId(id)) {
                throw ApiException.conflict(ErrorCode.INVALID_STATE, "Capacity is below the number of bookings");
            }
            c.setCapacity(req.capacity());
        }
        requireNoOverlap(c, id);
        return mapper.gymClass(classes.save(c));
    }

    public GymClassResponse cancel(AuthUser auth, Long id, CancelClassRequest req) {
        GymClass c = load(id);
        requireManager(auth, c);
        requireScheduled(c);

        // TODO(db): transaction
        Instant now = clock.instant();
        for (Booking b : bookings.findByClassIdAndStatus(id, BookingStatus.BOOKED)) {
            b.setStatus(BookingStatus.CANCELLED);
            b.setCancelledAt(now);
            bookings.save(b);
        }
        c.setStatus(ClassStatus.CANCELLED);
        c.setCancelReason(req == null ? null : req.reason());
        return mapper.gymClass(classes.save(c));
    }

    public List<BookingResponse> listBookings(AuthUser auth, Long id) {
        GymClass c = load(id);
        if (auth.role() == Role.TRAINER) {
            requireManager(auth, c);
        }
        return bookings.findByClassId(id).stream().map(mapper::booking).toList();
    }

    private GymClass load(Long id) {
        return classes.findById(id).orElseThrow(() -> ApiException.notFound("Class"));
    }

    private void requireManager(AuthUser auth, GymClass c) {
        if (auth.role() != Role.ADMIN && !permissions.isTrainerOf(auth, c)) {
            throw ApiException.forbidden();
        }
    }

    private void requireScheduled(GymClass c) {
        if (c.getStatus() != ClassStatus.SCHEDULED) {
            throw ApiException.conflict(ErrorCode.INVALID_STATE, "Class is not scheduled");
        }
    }

    private void requireTrainer(Long trainerId) {
        if (trainers.findById(trainerId).isEmpty()) {
            throw ApiException.validation("trainerId", "Trainer does not exist");
        }
    }

    private void requireFuture(Instant start) {
        if (!start.isAfter(clock.instant())) {
            throw ApiException.validation("startTime", "Start time must be in the future");
        }
    }

    private void requireNoOverlap(GymClass c, Long excludeId) {
        if (!classes.findOverlapping(c.getTrainerId(), c.getRoom(), c.getStartTime(), c.endTime(), excludeId)
                .isEmpty()) {
            throw ApiException.conflict(ErrorCode.SCHEDULE_CONFLICT, "Trainer or room is busy at that time");
        }
    }
}
