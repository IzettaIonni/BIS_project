package kz.gymbooking.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import kz.gymbooking.domain.Membership;
import kz.gymbooking.domain.MembershipStatus;

public interface MembershipRepository {

    Membership save(Membership membership);

    /** Ordered by startDate descending. */
    List<Membership> findByClientId(Long clientId);

    List<Membership> findByClientIdAndStatusIn(Long clientId, Collection<MembershipStatus> statuses);

    List<Membership> findActiveEndedBefore(LocalDate date);
}
