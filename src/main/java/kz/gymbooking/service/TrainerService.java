package kz.gymbooking.service;

import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.Trainer;
import kz.gymbooking.domain.User;
import kz.gymbooking.dto.PageResponse;
import kz.gymbooking.dto.TrainerCreate;
import kz.gymbooking.dto.TrainerResponse;
import kz.gymbooking.dto.TrainerUpdate;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.error.ErrorCode;
import kz.gymbooking.repository.TrainerRepository;
import kz.gymbooking.repository.UserRepository;
import kz.gymbooking.security.AuthUser;
import org.springframework.stereotype.Service;

@Service
public class TrainerService {

    private final TrainerRepository trainers;
    private final UserRepository users;
    private final ResponseMapper mapper;

    public TrainerService(TrainerRepository trainers, UserRepository users, ResponseMapper mapper) {
        this.trainers = trainers;
        this.users = users;
        this.mapper = mapper;
    }

    public PageResponse<TrainerResponse> list(String specialization, int limit, int offset) {
        return PageResponse.of(trainers.search(specialization, limit, offset), mapper::trainer);
    }

    public TrainerResponse get(Long id) {
        return mapper.trainer(load(id));
    }

    public TrainerResponse create(TrainerCreate req) {
        User user = users.findById(req.userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (trainers.findByUserId(user.getId()).isPresent()) {
            throw ApiException.conflict(ErrorCode.ALREADY_EXISTS, "Trainer profile already exists");
        }
        Trainer trainer = new Trainer();
        trainer.setUserId(user.getId());
        trainer.setSpecializations(req.specializations() == null ? java.util.List.of() : req.specializations());
        trainer.setBio(req.bio());
        // TODO(db): transaction (trainer save + user role save)
        Trainer saved = trainers.save(trainer);
        user.setRole(Role.TRAINER);
        users.save(user);
        return mapper.trainer(saved);
    }

    public TrainerResponse update(AuthUser auth, Long id, TrainerUpdate req) {
        Trainer trainer = load(id);
        boolean owner = auth.role() == Role.TRAINER && trainer.getUserId().equals(auth.id());
        if (auth.role() != Role.ADMIN && !owner) {
            throw ApiException.forbidden();
        }
        if (req.specializations() != null) {
            trainer.setSpecializations(req.specializations());
        }
        if (req.bio() != null) {
            trainer.setBio(req.bio());
        }
        if (req.photoUrl() != null) {
            trainer.setPhotoUrl(req.photoUrl().toString());
        }
        return mapper.trainer(trainers.save(trainer));
    }

    private Trainer load(Long id) {
        return trainers.findById(id).orElseThrow(() -> ApiException.notFound("Trainer"));
    }
}
