public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {}