package kz.gymbooking.repository;

import java.util.Optional;

import kz.gymbooking.domain.Trainer;

public interface TrainerRepository {

    Optional<Trainer> findById(Long id);

    Optional<Trainer> findByUserId(Long userId);

    Trainer save(Trainer trainer);

    Page<Trainer> search(String specialization, int limit, int offset);
}
