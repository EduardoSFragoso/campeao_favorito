package br.com.favorito.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthDtos() {
  public record RegisterRequest(
      @NotBlank @Email String email,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank @Size(min = 2, max = 40) String displayName) {}

  public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

  public record AuthResponse(String token, UserResponse user) {}

  public record UserResponse(Long id, String email, String displayName) {
    public static UserResponse from(User u) {
      return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName());
    }
  }
}
