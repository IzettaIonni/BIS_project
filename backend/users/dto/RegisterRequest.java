package com.example.gym.users.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank String fullName,
        @Email @NotBlank String email,
        @Size(min = 8, message = "Пароль минимум 8 символов") String password
) {}