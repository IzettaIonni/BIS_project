package kz.gymbooking.controller;

import jakarta.validation.Valid;
import kz.gymbooking.dto.LoginRequest;
import kz.gymbooking.dto.RefreshRequest;
import kz.gymbooking.dto.RegisterRequest;
import kz.gymbooking.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/auth", produces = "text/plain;charset=UTF-8")
public class AuthController {

    private static final String TOKENS = """
            accessToken: stub-access-token
            refreshToken: stub-refresh-token
            expiresIn: 900
            """;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public String register(@Valid @RequestBody RegisterRequest req) {
        if (req.email().equals("taken@example.com")) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
        }
        return TOKENS + "user: #18 " + req.fullName() + ", " + req.email() + ", CLIENT\n";
    }

    @PostMapping("/login")
    public String login(@Valid @RequestBody LoginRequest req) {
        if (req.password().equals("wrong")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Wrong email or password");
        }
        return TOKENS + "user: #17 Aruzhan Bekova, aruzhan@example.com, CLIENT\n";
    }

    @PostMapping("/refresh")
    public String refresh(@Valid @RequestBody RefreshRequest req) {
        return TOKENS;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
    }
}
