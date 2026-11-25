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
  private final VerificacaoEmailService verificacao;
  private final CurrentUserService currentUser;

  /**
   * Cadastro com resposta ÚNICA (opção A do dono, anti-enumeração): 201 + mensagem igual para conta
   * nova, pendente e confirmada — quem já tem conta não é revelado. Rate limit mantido: contas
   * pendentes disparam e-mail novo, então o teto guard no abuso.
   */
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthDtos.MensagemResponse register(
      @Valid @RequestBody AuthDtos.RegisterRequest req, HttpServletRequest http) {
    // prod: IP real via X-Forwarded-For (nginx) graças ao forward-headers-strategy
    registerLimiter.check(http.getRemoteAddr());
    return authService.register(req);
  }

  /** Confirmação do e-mail (link do cadastro). Pública: quem chega é quem tem o token. */
  @GetMapping("/verificar")
  public AuthDtos.MensagemResponse verificar(@RequestParam("token") String token) {
    verificacao.verificar(token);
    return new AuthDtos.MensagemResponse("E-mail confirmado — já pode entrar.");
  }

  /**
   * Renvio do e-mail de verificação. Mesmo teto de cadastro (5/min por IP, os dois pares
   * origem↔caixa). Resposta SEMPRE igual — não revela se o e-mail existe nem está confirmado.
   */
  @PostMapping("/reenviar-verificacao")
  public AuthDtos.MensagemResponse reenviarVerificacao(
      @Valid @RequestBody AuthDtos.ReenvioRequest req, HttpServletRequest http) {
    registerLimiter.check(http.getRemoteAddr());
    verificacao.reenviar(req.email());
    return new AuthDtos.MensagemResponse(
        "Se este e-mail existir e estiver pendente, um novo link foi enviado.");
  }

  @PostMapping("/login")
  public AuthDtos.AuthResponse login(
      @Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest http) {
    // prod: IP real via X-Forwarded-For (nginx) graças ao forward-headers-strategy
    loginLimiter.check(http.getRemoteAddr());
    return authService.login(req);
  }

  /**
   * Revoga a sessão no servidor (token_ver++); o cliente descarta o token local. 204, sem corpo.
   */
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {
    authService.logout(currentUser.userId());
  }
}
