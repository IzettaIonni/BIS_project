package kz.gymbooking.job;

import java.time.Clock;
import java.time.LocalDate;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.BookingStatus;
import kz.gymbooking.domain.ClassStatus;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Membership;
import kz.gymbooking.domain.MembershipStatus;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.GymClassRepository;
import kz.gymbooking.repository.MembershipRepository;
import kz.gymbooking.service.MembershipService;
import org.springframework.stereotype.Component;

@Component
public class StatusJobs {

    private final GymClassRepository classes;
    private final BookingRepository bookings;
    private final MembershipRepository memberships;
    private final MembershipService membershipService;
    private final Clock clock;

    public StatusJobs(GymClassRepository classes, BookingRepository bookings, MembershipRepository memberships,
                      MembershipService membershipService, Clock clock) {
        this.classes = classes;
        this.bookings = bookings;
        this.memberships = memberships;
        this.membershipService = membershipService;
        this.clock = clock;
    }

    /** Marks ended SCHEDULED classes FINISHED and their still-BOOKED bookings NO_SHOW. */
    public void finishEndedClasses() {
        for (GymClass gymClass : classes.findScheduledEndedBefore(clock.instant())) {
            // bookings first, class last: a mid-run failure leaves the class SCHEDULED and the next run retries
            for (Booking booking : bookings.findByClassIdAndStatus(gymClass.getId(), BookingStatus.BOOKED)) {
                booking.setStatus(BookingStatus.NO_SHOW);
                bookings.save(booking);
            }
            gymClass.setStatus(ClassStatus.FINISHED);
            classes.save(gymClass);
        }
    }

    /** Marks ACTIVE memberships whose end date is before today (gym zone) EXPIRED. */
    public void expireMemberships() {
        LocalDate today = membershipService.today();
        for (Membership membership : memberships.findActiveEndedBefore(today)) {
            membership.setStatus(MembershipStatus.EXPIRED);
            memberships.save(membership);
        }
    }
}
