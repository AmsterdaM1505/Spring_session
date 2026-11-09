package ru.mephi.booking.api;

import jakarta.validation.constraints.*;
import ru.mephi.booking.domain.AppUser.Role;

public final class UserDtos {
    private UserDtos() {
    }
    public record Credentials(@NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}") String username,
    @NotBlank @Size(min = 10, max = 72) String password) {
    }
    public record NewUser(@NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}") String username,
    @NotBlank @Size(min = 10, max = 72) String password, @NotNull Role role) {
    }
    public record UpdateUser(@Pattern(regexp = "[A-Za-z0-9_.-]{3,64}") String username,
    @Size(min = 10, max = 72) String password, Role role) {
    }
    public record UserView(Long id, String username, Role role, boolean active) {
    }
    public record TokenView(String accessToken, String tokenType, long expiresIn, UserView user) {
    }
}
