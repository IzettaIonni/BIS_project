package kz.gymbooking.repository;

import java.util.List;
import java.util.Optional;

import kz.gymbooking.domain.MembershipPlan;

public interface MembershipPlanRepository {

    List<MembershipPlan> findAll();

    Optional<MembershipPlan> findById(Long id);
}
