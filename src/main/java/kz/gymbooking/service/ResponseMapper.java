package kz.gymbooking.service;

import kz.gymbooking.domain.User;
import kz.gymbooking.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class ResponseMapper {

    public UserResponse user(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole(),
                u.getCreatedAt());
    }
}
