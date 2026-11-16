package kz.gymbooking.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.BookingStatus;
import kz.gymbooking.domain.CheckInDecision;
import kz.gymbooking.domain.DenyReason;
import kz.gymbooking.domain.Membership;
import kz.gymbooking.domain.MembershipStatus;
import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.User;
import kz.gymbooking.domain.Visit;
import kz.gymbooking.dto.CheckInRequest;
import kz.gymbooking.dto.CheckInResponse;
import kz.gymbooking.dto.PageResponse;
import kz.gymbooking.dto.QrTokenResponse;
import kz.gymbooking.dto.UserSummary;
import kz.gymbooking.dto.VisitResponse;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.error.ErrorCode;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.MembershipRepository;
import kz.gymbooking.repository.UsedQrNonceRepository;
import kz.gymbooking.repository.UserRepository;
import kz.gymbooking.repository.VisitRepository;
import kz.gymbooking.security.AuthUser;
import kz.gymbooking.security.JwtProperties;
import kz.gymbooking.security.QrTokenException;
import kz.gymbooking.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AccessService {

    private static final Duration BOOKING_WINDOW = Duration.ofMinutes(30);
    private static final String DEFAULT_GATE = "main";

    private final TokenService tokens;
    private final JwtProperties props;
    private final MembershipService membershipService;
    private final MembershipRepository memberships;
    private final UsedQrNonceRepository usedNonces;
    private final BookingRepository bookings;
    private final VisitRepository visits;
    private final UserRepository users;
    private final ResponseMapper mapper;
    private final Clock clock;
    private final RandomGenerator random;

    public AccessService(TokenService tokens, JwtProperties props, MembershipService membershipService,
                         MembershipRepository memberships, UsedQrNonceRepository usedNonces,
                         BookingRepository bookings, VisitRepository visits, UserRepository users,
                         ResponseMapper mapper, Clock clock, RandomGenerator random) {
        this.tokens = tokens;
        this.props = props;
        this.membershipService = membershipService;
        this.memberships = memberships;
        this.usedNonces = usedNonces;
        this.bookings = bookings;
        this.visits = visits;
        this.users = users;
        this.mapper = mapper;
        this.clock = clock;
        this.random = random;
    }

    public QrTokenResponse issueQr(AuthUser auth) {
        if (membershipService.findUsable(auth.id(), membershipService.today()).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.NO_ACTIVE_MEMBERSHIP,
                    "No active membership to enter with");
        }
        String nonce = new UUID(random.nextLong(), random.nextLong()).toString();
        Instant expiresAt = clock.instant().plus(props.getQrTtl());
        return new QrTokenResponse(tokens.issueQr(auth.id(), nonce, expiresAt), expiresAt);
    }

    public CheckInResponse checkIn(CheckInRequest req) {
        TokenService.QrClaims claims;
        try {
            claims = tokens.parseQr(req.token());
        } catch (QrTokenException e) {
            return denied(e.getReason(), null);
        }
        if (usedNonces.existsByNonce(claims.nonce())) {
            return denied(DenyReason.TOKEN_ALREADY_USED, null);
        }
        Optional<User> user = users.findById(claims.userId());
        if (user.isEmpty()) {
            return denied(DenyReason.TOKEN_INVALID, null);
        }
        UserSummary summary = new UserSummary(user.get().getId(), user.get().getFullName(), null);
        List<Membership> covering = membershipService.findCovering(claims.userId(), membershipService.today());
        if (covering.isEmpty()) {
            return denied(DenyReason.NO_ACTIVE_MEMBERSHIP, summary);
        }
        Optional<Membership> usable = covering.stream().filter(Membership::hasVisitsLeft).findFirst();
        if (usable.isEmpty()) {
            return denied(DenyReason.NO_VISITS_LEFT, summary);
        }

        // TODO(db): transaction
        Instant now = clock.instant();
        // markUsed is atomic: a concurrent scan of the same QR loses here and nothing else is written
        if (!usedNonces.markUsed(claims.nonce(), claims.expiresAt())) {
            return denied(DenyReason.TOKEN_ALREADY_USED, null);
        }

        Membership membership = usable.get();
        if (membership.getVisitsLeft() != null) {
            membership.setVisitsLeft(membership.getVisitsLeft() - 1);
            if (membership.getVisitsLeft() == 0) {
                membership.setStatus(MembershipStatus.EXPIRED);
            }
            membership = memberships.save(membership);
        }

        Booking attended = bookings.findBookedByClientWithClassStartBetween(
                        claims.userId(), now.minus(BOOKING_WINDOW), now.plus(BOOKING_WINDOW))
                .stream().findFirst().orElse(null);
        if (attended != null) {
            attended.setStatus(BookingStatus.ATTENDED);
            attended.setCheckedInAt(now);
            attended = bookings.save(attended);
        }

        Visit visit = new Visit();
        visit.setUserId(claims.userId());
        visit.setGate(req.gate() == null || req.gate().isBlank() ? DEFAULT_GATE : req.gate());
        visit.setCheckedInAt(now);
        visit.setBookingId(attended == null ? null : attended.getId());
        visit = visits.save(visit);

        return new CheckInResponse(CheckInDecision.GRANTED, null, summary, mapper.membership(membership),
                attended == null ? null : mapper.booking(attended), visit.getId());
    }

    public PageResponse<VisitResponse> listVisits(AuthUser auth, String userId, Instant from, Instant to,
                                                  int limit, int offset) {
        Long target = resolveVisitUser(auth, userId);
        return PageResponse.of(visits.search(target, from, to, limit, offset), v ->
                new VisitResponse(v.getId(), v.getUserId(), v.getGate(), v.getCheckedInAt(), v.getBookingId()));
    }

    private static Long resolveVisitUser(AuthUser auth, String userId) {
        boolean privileged = auth.role() == Role.STAFF || auth.role() == Role.ADMIN;
        if (userId == null) {
            if (privileged) {
                return null;
            }
            throw ApiException.forbidden();
        }
        if ("me".equals(userId)) {
            return auth.id();
        }
        Long id;
        try {
            id = Long.valueOf(userId);
        } catch (NumberFormatException e) {
            if (!privileged) {
                throw ApiException.forbidden();
            }
            throw ApiException.validation("userId", "must be a number or 'me'");
        }
        if (!privileged && !id.equals(auth.id())) {
            throw ApiException.forbidden();
        }
        return id;
    }

    private static CheckInResponse denied(DenyReason reason, UserSummary user) {
        return new CheckInResponse(CheckInDecision.DENIED, reason, user, null, null, null);
    }
}
