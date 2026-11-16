package kz.gymbooking.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.BookingStatus;
import kz.gymbooking.domain.ClassStatus;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Role;
import kz.gymbooking.dto.BookingResponse;
import kz.gymbooking.dto.CreateBookingRequest;
import kz.gymbooking.dto.PageResponse;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.error.ErrorCode;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.GymClassRepository;
import kz.gymbooking.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

    private static final Duration CANCEL_DEADLINE = Duration.ofHours(2);

    private final BookingRepository bookings;
    private final GymClassRepository classes;
    private final MembershipService memberships;
    private final ResponseMapper mapper;
    private final Permissions permissions;
    private final Clock clock;
    private final ZoneId gymZone;

    public BookingService(BookingRepository bookings, GymClassRepository classes, MembershipService memberships,
                          ResponseMapper mapper, Permissions permissions, Clock clock, ZoneId gymZone) {
        this.bookings = bookings;
        this.classes = classes;
        this.memberships = memberships;
        this.mapper = mapper;
        this.permissions = permissions;
        this.clock = clock;
        this.gymZone = gymZone;
    }

    // TODO(db): transaction
    public BookingResponse create(AuthUser auth, CreateBookingRequest req, UUID idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Booking> existing = bookings.findByClientIdAndIdempotencyKey(auth.id(), idempotencyKey);
            if (existing.isPresent()) {
                return mapper.booking(existing.get());
            }
        }
        GymClass c = classes.findByIdForUpdate(req.classId()).orElseThrow(() -> ApiException.notFound("Class"));
        Instant now = clock.instant();
        if (c.getStatus() != ClassStatus.SCHEDULED || !c.getStartTime().isAfter(now)) {
            throw ApiException.conflict(ErrorCode.CLASS_NOT_BOOKABLE, "Class is not open for booking");
        }
        LocalDate sessionDate = c.getStartTime().atZone(gymZone).toLocalDate();
        if (memberships.findUsable(auth.id(), sessionDate).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.NO_ACTIVE_MEMBERSHIP,
                    "No active membership covers this class");
        }
        if (bookings.findActiveByClientIdAndClassId(auth.id(), c.getId()).isPresent()) {
            throw ApiException.conflict(ErrorCode.ALREADY_BOOKED, "You already booked this class");
        }
        if (bookings.countActiveByClassId(c.getId()) >= c.getCapacity()) {
            throw ApiException.conflict(ErrorCode.CLASS_FULL, "Class is full");
        }
        Booking b = new Booking();
        b.setClientId(auth.id());
        b.setClassId(c.getId());
        b.setStatus(BookingStatus.BOOKED);
        b.setBookedAt(now);
        b.setIdempotencyKey(idempotencyKey);
        return mapper.booking(bookings.save(b));
    }

    public PageResponse<BookingResponse> listMine(AuthUser auth, BookingStatus status, Boolean upcoming,
                                                  int limit, int offset) {
        return PageResponse.of(
                bookings.searchByClient(auth.id(), status, upcoming, clock.instant(), limit, offset),
                mapper::booking);
    }

    public BookingResponse get(AuthUser auth, Long id) {
        Booking b = load(id);
        boolean allowed = b.getClientId().equals(auth.id())
                || auth.role() == Role.STAFF || auth.role() == Role.ADMIN
                || isClassTrainer(auth, b);
        if (!allowed) {
            throw ApiException.forbidden();
        }
        return mapper.booking(b);
    }

    public BookingResponse cancel(AuthUser auth, Long id) {
        Booking b = load(id);
        boolean admin = auth.role() == Role.ADMIN;
        if (!admin && !b.getClientId().equals(auth.id())) {
            throw ApiException.forbidden();
        }
        if (b.getStatus() != BookingStatus.BOOKED) {
            throw ApiException.conflict(ErrorCode.INVALID_STATE, "Booking is not active");
        }
        Instant now = clock.instant();
        if (!admin) {
            GymClass c = classes.findById(b.getClassId()).orElseThrow(() -> ApiException.notFound("Class"));
            if (now.isAfter(c.getStartTime().minus(CANCEL_DEADLINE))) {
                throw ApiException.conflict(ErrorCode.CANCEL_DEADLINE_PASSED,
                        "Bookings can only be cancelled up to 2 hours before the class");
            }
        }
        b.setStatus(BookingStatus.CANCELLED);
        b.setCancelledAt(now);
        return mapper.booking(bookings.save(b));
    }

    private Booking load(Long id) {
        return bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking"));
    }

    private boolean isClassTrainer(AuthUser auth, Booking b) {
        return auth.role() == Role.TRAINER
                && classes.findById(b.getClassId()).filter(c -> permissions.isTrainerOf(auth, c)).isPresent();
    }
}
