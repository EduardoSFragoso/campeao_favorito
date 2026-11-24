package br.com.favorito.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final LoginRateLimiter loginLimiter;
  private final RegisterRateLimiter registerLimiter;

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthDtos.AuthResponse register(
      @Valid @RequestBody AuthDtos.RegisterRequest req, HttpServletRequest http) {
    // prod: IP real via X-Forwarded-For (nginx) graças ao forward-headers-strategy
    registerLimiter.check(http.getRemoteAddr());
    return authService.register(req);
  }

  @PostMapping("/login")
  public AuthDtos.AuthResponse login(
      @Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest http) {
    // prod: IP real via X-Forwarded-For (nginx) graças ao forward-headers-strategy
    loginLimiter.check(http.getRemoteAddr());
    return authService.login(req);
  }
}
