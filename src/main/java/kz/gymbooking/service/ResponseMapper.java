package kz.gymbooking.service;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Trainer;
import kz.gymbooking.domain.User;
import kz.gymbooking.dto.BookingResponse;
import kz.gymbooking.dto.GymClassResponse;
import kz.gymbooking.dto.TrainerRef;
import kz.gymbooking.dto.TrainerResponse;
import kz.gymbooking.dto.UserResponse;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.GymClassRepository;
import kz.gymbooking.repository.TrainerRepository;
import kz.gymbooking.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class ResponseMapper {

    private final UserRepository users;
    private final TrainerRepository trainers;
    private final GymClassRepository classes;
    private final BookingRepository bookings;

    public ResponseMapper(UserRepository users, TrainerRepository trainers, GymClassRepository classes,
                          BookingRepository bookings) {
        this.users = users;
        this.trainers = trainers;
        this.classes = classes;
        this.bookings = bookings;
    }

    public UserResponse user(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole(),
                u.getCreatedAt());
    }

    public TrainerResponse trainer(Trainer t) {
        return new TrainerResponse(t.getId(), t.getUserId(), trainerName(t), t.getSpecializations(), t.getBio(),
                t.getPhotoUrl());
    }

    public GymClassResponse gymClass(GymClass c) {
        Trainer trainer = trainers.findById(c.getTrainerId()).orElseThrow(() -> ApiException.notFound("Trainer"));
        return new GymClassResponse(c.getId(), c.getName(), c.getType(),
                new TrainerRef(trainer.getId(), trainerName(trainer)), c.getStartTime(), c.getDurationMin(),
                c.getRoom(), c.getCapacity(), bookings.countActiveByClassId(c.getId()), c.getStatus());
    }

    public BookingResponse booking(Booking b) {
        GymClass c = classes.findById(b.getClassId()).orElseThrow(() -> ApiException.notFound("Class"));
        return new BookingResponse(b.getId(), b.getClientId(), gymClass(c), b.getStatus(), b.getBookedAt(),
                b.getCancelledAt(), b.getCheckedInAt());
    }

    private String trainerName(Trainer t) {
        return users.findById(t.getUserId()).map(User::getFullName).orElse(null);
    }
}
