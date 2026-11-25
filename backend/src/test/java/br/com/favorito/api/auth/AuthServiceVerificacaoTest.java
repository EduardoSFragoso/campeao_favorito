package br.com.favorito.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cadastro com verificação de e-mail: register não autentica (sem JWT) e login de usuário sem
 * e-mail confirmado é bloqueado com mensagem acionável. Falham no código antigo (register devolvia
 * token, login de usuário não verificado passava reto).
 */
class AuthServiceVerificacaoTest {

  private UserRepository users;
  private PasswordEncoder encoder;
  private JwtService jwt;
  private VerificacaoEmailService verificacao;
  private AuthService service;

  private User user(String email, boolean verificado) {
    User u = new User();
    u.setEmail(email);
    u.setDisplayName("Ana");
    u.setPasswordHash("hash");
    u.setEmailVerificado(verificado);
    return u;
  }

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    encoder = mock(PasswordEncoder.class);
    jwt = mock(JwtService.class);
    verificacao = mock(VerificacaoEmailService.class);
    service = new AuthService(users, encoder, jwt, verificacao);
  }

  @Test
  @DisplayName("register entra NÃO verificado e a resposta NÃO carrega JWT")
  void registerNaoAutentica() {
    when(users.existsByEmailIgnoreCase(any())).thenReturn(false);
    when(users.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    AuthDtos.CadastroResponse res =
        service.register(new AuthDtos.RegisterRequest("ana@exemplo.dev", "12345678", "Ana"));

    assertInstanceOf(AuthDtos.CadastroResponse.class, res);
    assertEquals("ana@exemplo.dev", res.user().email());
    // o envio do e-mail é passo obrigatório do cadastro
    org.mockito.Mockito.verify(verificacao).criar(any());
  }

  @Test
  @DisplayName("login sem e-mail verificado → bloqueio com mensagem acionável")
  void loginSemVerificacaoEBloqueado() {
    Mockito.lenient().when(encoder.matches(any(), any())).thenReturn(true);
    when(users.findByEmailIgnoreCase("ana@exemplo.dev"))
        .thenReturn(java.util.Optional.of(user("ana@exemplo.dev", false)));

    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () -> service.login(new AuthDtos.LoginRequest("ana@exemplo.dev", "12345678")));
    assertEquals("Confirme o e-mail antes de entrar.", ex.getMessage());
  }

  @Test
  @DisplayName("login com e-mail verificado segue o fluxo normal com JWT")
  void loginVerificadoSeguiNormal() {
    Mockito.lenient().when(encoder.matches(any(), any())).thenReturn(true);
    when(users.findByEmailIgnoreCase("ana@exemplo.dev"))
        .thenReturn(java.util.Optional.of(user("ana@exemplo.dev", true)));
    when(jwt.issue(any())).thenReturn("jwt-feliz");

    AuthDtos.AuthResponse res =
        service.login(new AuthDtos.LoginRequest("ana@exemplo.dev", "12345678"));

    assertEquals("jwt-feliz", res.token());
  }
}
