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
  @DisplayName("register entra NÃO verificado, resposta genérica SEM JWT e SEM dados da conta")
  void registerNaoAutentica() {
    when(users.findByEmailIgnoreCase(any()))
        .thenReturn(java.util.Optional.empty()); // e-mail ainda não existe
    when(users.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    var res = service.register(new AuthDtos.RegisterRequest("ana@exemplo.dev", "12345678", "Ana"));

    assertInstanceOf(AuthDtos.MensagemResponse.class, res);
    assertEquals(AuthService.MENSAGEM_REGISTER, res.message());
    // o envio do e-mail é passo obrigatório do cadastro
    org.mockito.Mockito.verify(verificacao).criar(any());
  }

  @Test
  @DisplayName("corrida perdida (users_email_key): MESMA resposta genérica — sem 409, sem dados")
  void registerPerdedorDaCorridaTemMesmaResposta() {
    when(users.findByEmailIgnoreCase(any()))
        .thenReturn(java.util.Optional.empty()); // janela da corrida: visto antes do INSERT
    when(users.saveAndFlush(any()))
        .thenThrow(br.com.favorito.api.support.ViolacaoDeBanco.em("users_email_key"));

    var res = service.register(new AuthDtos.RegisterRequest("ana@exemplo.dev", "12345678", "Ana"));

    assertEquals(AuthService.MENSAGEM_REGISTER, res.message());
  }

  /**
   * Anti-cronometragem (timing): o caminho de e-mail existente também precisa gastar o trabalho do
   * bcrypt — se só o caminho novo codifica, o tempo de resposta revela que a conta existe. Falha no
   * código antigo, que codificava apenas a senha do cadastro novo.
   */
  @Test
  @DisplayName(
      "register de e-mail EXISTENTE também roda o bcrypt (tempo equivalente nos 2 caminhos)")
  void registerContaExistenteTambemCodifica() {
    when(users.findByEmailIgnoreCase(any()))
        .thenReturn(java.util.Optional.of(user("ana@exemplo.dev", false)));

    var res = service.register(new AuthDtos.RegisterRequest("ana@exemplo.dev", "12345678", "Ana"));

    assertEquals(AuthService.MENSAGEM_REGISTER, res.message());
    org.mockito.Mockito.verify(encoder).encode("12345678");
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
