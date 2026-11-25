package br.com.favorito.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Contrato de REVOGAÇÃO no filtro: o JWT é válido criptograficamente, mas só autentica se a versão
 * da sessão gravada no token for igual à do banco (token_ver). Versão divergente ou usuário sumido
 * = sem autenticação (a rota responde 401/403 lá na frente).
 */
class JwtAuthFilterTest {

  private final JwtService jwtService = mock(JwtService.class);
  private final UserRepository users = mock(UserRepository.class);
  private final JwtAuthFilter filtro = new JwtAuthFilter(jwtService, users);

  @BeforeEach
  @AfterEach
  void limparContexto() {
    SecurityContextHolder.clearContext();
  }

  /** Roda o filtro com um Bearer e devolve a autenticação instalada (ou null). */
  private Authentication rodarComToken(String token) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    filtro.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
    return SecurityContextHolder.getContext().getAuthentication();
  }

  private User usuario(long id, short tokenVer) {
    User u = new User();
    u.setId(id);
    u.setTokenVer(tokenVer);
    return u;
  }

  @Test
  void tokenComVersaoAtualAutentica() throws Exception {
    when(jwtService.validate("t-ok")).thenReturn("42");
    when(jwtService.versaoDoToken("t-ok")).thenReturn(2);
    when(users.findById(42L)).thenReturn(Optional.of(usuario(42L, (short) 2)));

    Authentication auth = rodarComToken("t-ok");

    assertTrue(auth != null && auth.isAuthenticated());
    assertEquals("42", auth.getPrincipal());
  }

  @Test
  void tokenDeSessaoRevogadaNaoAutentica() throws Exception {
    // token carrega ver=0, mas o usuário já passou por logout: banco tem ver=1
    when(jwtService.validate("t-antigo")).thenReturn("42");
    when(jwtService.versaoDoToken("t-antigo")).thenReturn(0);
    when(users.findById(42L)).thenReturn(Optional.of(usuario(42L, (short) 1)));

    assertFalse(rodarComToken("t-antigo") != null);
  }

  @Test
  void usuarioInexistenteNaoAutentica() throws Exception {
    when(jwtService.validate("7")).thenReturn("7");
    when(jwtService.versaoDoToken("7")).thenReturn(0);
    when(users.findById(7L)).thenReturn(Optional.empty());

    assertFalse(rodarComToken("7") != null);
  }
}
