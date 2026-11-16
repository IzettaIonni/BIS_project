package kz.gymbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.BookingStatus;
import kz.gymbooking.domain.ClassStatus;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Membership;
import kz.gymbooking.domain.MembershipStatus;
import kz.gymbooking.job.StatusJobs;
import kz.gymbooking.support.Fixtures;
import kz.gymbooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

class JobsIT extends IntegrationTest {

    @Autowired StatusJobs jobs;

    @Test
    void endedClassFinishesAndNoShows() {
        GymClass ended = Fixtures.gymClass();
        ended.setStatus(ClassStatus.SCHEDULED);
        Booking booked = Fixtures.booking();
        booked.setStatus(BookingStatus.BOOKED);
        when(classes.findScheduledEndedBefore(NOW)).thenReturn(List.of(ended));
        when(bookings.findByClassIdAndStatus(ended.getId(), BookingStatus.BOOKED)).thenReturn(List.of(booked));

        jobs.finishEndedClasses();

        ArgumentCaptor<GymClass> savedClass = ArgumentCaptor.forClass(GymClass.class);
        verify(classes).save(savedClass.capture());
        assertThat(savedClass.getValue().getStatus()).isEqualTo(ClassStatus.FINISHED);
        ArgumentCaptor<Booking> savedBooking = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(savedBooking.capture());
        assertThat(savedBooking.getValue().getStatus()).isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    void expiredMembershipsMarked() {
        Membership old = Fixtures.membership();
        old.setEndDate(LocalDate.of(2026, 11, 15));
        old.setStatus(MembershipStatus.ACTIVE);
        when(memberships.findActiveEndedBefore(LocalDate.of(2026, 11, 16))).thenReturn(List.of(old));

        jobs.expireMemberships();

        ArgumentCaptor<Membership> saved = ArgumentCaptor.forClass(Membership.class);
        verify(memberships).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(MembershipStatus.EXPIRED);
    }
}
