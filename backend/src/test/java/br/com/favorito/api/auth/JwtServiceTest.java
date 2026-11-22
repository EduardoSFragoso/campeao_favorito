package br.com.favorito.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/** Contrato do token: round-trip, expiração, token adulterado e o fail-fast do perfil prod. */
class JwtServiceTest {

  private static final String SECRET = "1234567890abcdef".repeat(4); // >= 32 bytes p/ HS256
  private final User user = novoUsuario();

  private User novoUsuario() {
    User u = new User();
    u.setId(42L);
    u.setEmail("teste@teste.dev");
    u.setDisplayName("Testador");
    return u;
  }

  private JwtService service(Environment env) {
    return new JwtService(SECRET, 604800000L, env);
  }

  private JwtService service() {
    return service(new MockEnvironment());
  }

  @Test
  void roundTripTokensEValidation() {
    JwtService svc = service();
    String token = svc.issue(user);
    assertNotNull(token);
    assertEquals("42", svc.validate(token));
  }

  @Test
  void tokenExpiradoIvalido() {
    JwtService servicoExpirado = new JwtService(SECRET, -1000L, new MockEnvironment());
    String token = servicoExpirado.issue(user);
    assertNull(service().validate(token)); // outro serviço com clock "depois" da expiração
  }

  @Test
  void tokenAdulteradoIvalido() {
    JwtService svc = service();
    String token = svc.issue(user) + "x";
    assertNull(svc.validate(token));
  }

  @Test
  void lixoNaoEToken() {
    assertNull(service().validate("nao-e-um-jwt"));
    assertNull(service().validate(""));
    assertNull(service().validate(null));
  }

  @Test
  void segredosDiferentesNaoSeValidamEntreSi() {
    String token = service().issue(user);
    JwtService outro =
        new JwtService(SECRET.substring(0, 31) + "Z", 604800000L, new MockEnvironment());
    assertNull(outro.validate(token));
  }

  @Test
  void placeholderNoPerfilProdFalhaOBoot() {
    MockEnvironment prod = new MockEnvironment();
    prod.setActiveProfiles("prod");
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> new JwtService(JwtServicePlaceholderValue.VALUE, 1000L, prod));
    assertEquals(
        "JWT_SECRET não definida no perfil de produção. "
            + "Defina JWT_SECRET no EnvironmentFile (openssl rand -base64 64).",
        ex.getMessage());
  }

  @Test
  void placeholderForaDoPerfilProdContinuaAceito() {
    JwtService dev = new JwtService(JwtServicePlaceholderValue.VALUE, 1000L, new MockEnvironment());
    String token = dev.issue(user);
    assertEquals("42", dev.validate(token));
  }

  @Test
  void perfilSemProdNaoDispara() {
    Environment env = org.mockito.Mockito.mock(Environment.class);
    when(env.matchesProfiles(org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(false);
    assertNotNull(new JwtService(SECRET, 1000L, env));
  }
}

/** Acessa o placeholder privado de JwtService sem duplicar o valor em dois lugares. */
final class JwtServicePlaceholderValue {
  static final String VALUE = placeholder();

  private static String placeholder() {
    try {
      var field = JwtService.class.getDeclaredField("SECRET_PLACEHOLDER");
      field.setAccessible(true);
      return (String) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
