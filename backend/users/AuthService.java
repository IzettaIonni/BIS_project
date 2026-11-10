package com.example.gym.users;

import com.example.gym.users.dto.*;
import com.example.gym.users.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuthenticationManager authManager;

    public AuthService(UserRepository users, PasswordEncoder encoder,
                       JwtService jwt, AuthenticationManager authManager) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.authManager = authManager;
    }

    public AuthResponse register(RegisterRequest req) {
        String email = req.email().toLowerCase().trim();
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The email is already registered");
        }
        User user = users.save(new User(req.fullName(), email,
                encoder.encode(req.password()), Role.CLIENT));
        return toResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.email().toLowerCase().trim();
        authManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        User user = users.findByEmail(email).orElseThrow();
        return toResponse(user);
    }

    private AuthResponse toResponse(User u) {
        return new AuthResponse(jwt.generateToken(u), u.getEmail(), u.getFullName(), u.getRole().name());
    }
}