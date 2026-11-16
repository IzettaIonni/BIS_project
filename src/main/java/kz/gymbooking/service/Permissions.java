package kz.gymbooking.service;

import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.Trainer;
import kz.gymbooking.repository.TrainerRepository;
import kz.gymbooking.security.AuthUser;
import org.springframework.stereotype.Component;

@Component
public class Permissions {

    private final TrainerRepository trainers;

    public Permissions(TrainerRepository trainers) {
        this.trainers = trainers;
    }

    /** True when the caller has the TRAINER role and their trainer profile is the one teaching the class. */
    public boolean isTrainerOf(AuthUser user, GymClass gymClass) {
        if (user.role() != Role.TRAINER) {
            return false;
        }
        return trainers.findByUserId(user.id()).map(Trainer::getId)
                .filter(id -> id.equals(gymClass.getTrainerId())).isPresent();
    }
}
