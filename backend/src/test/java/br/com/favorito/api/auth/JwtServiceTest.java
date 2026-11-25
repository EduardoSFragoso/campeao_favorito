package br.com.favorito.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/** Contrato do token: round-trip, expiração, token adulterado e os fail-fast do guard do secret. */
class JwtServiceTest {

  private static final String SECRET =
      "1234567890abcdef".repeat(4); // >= 32 bytes p/ HS256 — gitleaks:allow (literal de teste)
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
  void tokenCarregaAVersaoDaSessaoDoUsuario() {
    user.setTokenVer((short) 3);
    JwtService svc = service();
    String token = svc.issue(user);
    assertEquals(3, svc.versaoDoToken(token));
    assertEquals("42", svc.validate(token)); // round-trip de sempre continua valendo
  }

  @Test
  void versaoDeTokenInvalidoENulo() {
    assertNull(service().versaoDoToken("lixo"));
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
            () -> new JwtService(JwtServicePlaceholderValue.VALUE, 60000L, prod));
    assertEquals(
        "JWT_SECRET não definida no perfil de produção. "
            + "Defina JWT_SECRET no EnvironmentFile (openssl rand -base64 64).",
        ex.getMessage());
  }

  @Test
  void placeholderSemPerfilExplicitoERecusado() {
    // no código anterior o placeholder era recusado só no perfil prod e aceito em qualquer
    // outro ambiente — agora sem perfil dev/test EXPLÍCITO ele é rejeitado de cara
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> new JwtService(JwtServicePlaceholderValue.VALUE, 60000L, new MockEnvironment()));
    assertEquals(
        "JWT_SECRET não definida: o placeholder do repositório só é aceito "
            + "nos perfis dev/test. Defina JWT_SECRET no ambiente (openssl rand -base64 64).",
        ex.getMessage());
  }

  @Test
  void segredoComMenosDe32BytesERecusado() {
    // no código anterior não era o guard: o io.jsonwebtoken recusava com WeakKeyException
    // (mensagem genérica de lib); a agora é uma regra nossa, com mensagem própria e exceção
    // do Spring context
    String curto = "chave-curta"; // < 32 bytes
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> new JwtService(curto, 60000L, new MockEnvironment()));
    assertEquals(
        "JWT_SECRET muito fraca: 11 bytes; o mínimo é 32 bytes (HS256)."
            + " Gere com openssl rand -base64 64.",
        ex.getMessage());
  }

  @Test
  void placeholderAceitoApenasComPerfilDevOuTestExplicito() {
    // dev explícito: placeholder segue funcionando (sprint local)
    MockEnvironment dev = new MockEnvironment();
    dev.setActiveProfiles("dev");
    JwtService comPlaceholder = new JwtService(JwtServicePlaceholderValue.VALUE, 60000L, dev);
    assertEquals("42", comPlaceholder.validate(comPlaceholder.issue(user)));

    // idem no perfil test (smoke e slices)
    MockEnvironment test = new MockEnvironment();
    test.setActiveProfiles("test");
    JwtService noTest = new JwtService(JwtServicePlaceholderValue.VALUE, 60000L, test);
    assertEquals("42", noTest.validate(noTest.issue(user)));
  }

  @Test
  void segredoCurtoRecusadoMesmoComPerfilDevOuTest() {
    // abaixo de 32 bytes o io.jsonwebtoken recusaria de qualquer forma (WeakKeyException);
    // a regra fecha antes, com mensagem acionável — aplicar "padding" em dev esconderia o
    // problema, então a exceção dev/test vale apenas para o placeholder (69 bytes, HS256 ok)
    String curto = "chave-curta"; // 11 bytes
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> new JwtService(curto, 60000L, ambiente("dev")));
    assertTrue(ex.getMessage().contains("muito fraca"));

    IllegalStateException exTest =
        assertThrows(
            IllegalStateException.class, () -> new JwtService(curto, 60000L, ambiente("test")));
    assertTrue(exTest.getMessage().contains("muito fraca"));
  }

  private MockEnvironment ambiente(String perfil) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(perfil);
    return env;
  }

  @Test
  void perfilSemProdNaoDispara() {
    Environment env = org.mockito.Mockito.mock(Environment.class);
    when(env.matchesProfiles(org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(false);
    assertNotNull(new JwtService(SECRET, 60000L, env));
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
