package kz.gymbooking.repository;

import java.util.Optional;

import kz.gymbooking.domain.Role;
import kz.gymbooking.domain.User;

public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    User save(User user);

    Page<User> search(Role role, String q, int limit, int offset);
}
