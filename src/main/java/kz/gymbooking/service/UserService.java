package kz.gymbooking.service;

import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.User;
import kz.gymbooking.dto.PageResponse;
import kz.gymbooking.dto.UserResponse;
import kz.gymbooking.dto.UserUpdate;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.RefreshTokenRepository;
import kz.gymbooking.repository.UserRepository;
import kz.gymbooking.security.AuthUser;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final ResponseMapper mapper;

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens, ResponseMapper mapper) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.mapper = mapper;
    }

    public UserResponse getMe(AuthUser auth) {
        return mapper.user(load(auth.id()));
    }

    public UserResponse updateMe(AuthUser auth, UserUpdate update) {
        User user = load(auth.id());
        if (update.fullName() != null) {
            user.setFullName(update.fullName());
        }
        if (update.phone() != null) {
            user.setPhone(update.phone());
        }
        return mapper.user(users.save(user));
    }

    public PageResponse<UserResponse> list(Role role, String q, int limit, int offset) {
        return PageResponse.of(users.search(role, q, limit, offset), mapper::user);
    }

    public UserResponse changeRole(Long userId, Role role) {
        User user = load(userId);
        user.setRole(role);
        User saved = users.save(user);
        refreshTokens.revokeAllByUserId(userId);
        return mapper.user(saved);
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }
}
