package br.com.favorito.api.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.favorito.api.common.InvalidCredentialsException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Sessão de ponta a ponta no serviço: login (jwt + refresh), refresh (rotação) e logout. */
class AuthServiceLogoutTest {

  private final UserRepository users = mock(UserRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final JwtService jwtService = mock(JwtService.class);
  private final VerificacaoEmailService verificacao = mock(VerificacaoEmailService.class);
  private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
  private final AuthService service =
      new AuthService(users, encoder, jwtService, verificacao, refreshTokens);

  private User usuario(long id, short tokenVer) {
    User u = new User();
    u.setId(id);
    u.setEmail("teste@teste.dev");
    u.setDisplayName("Testador");
    u.setPasswordHash("bcrypt");
    u.setEmailVerificado(true);
    u.setTokenVer(tokenVer);
    return u;
  }

  @Test
  void logoutIncrementaAVersaoESalvaERevogaOsRefreshes() {
    User u = usuario(7L, (short) 2);
    when(users.findById(7L)).thenReturn(Optional.of(u));

    service.logout(7L);

    assertEquals((short) 3, u.getTokenVer());
    verify(users).save(u);
    verify(refreshTokens).revogarTodosDoUsuario(7L);
  }

  @Test
  void logoutDeUsuarioInexistenteNaoFalha() {
    when(users.findById(99L)).thenReturn(Optional.empty());

    assertDoesNotThrow(() -> service.logout(99L));
    verify(users, never()).save(any(User.class));
  }

  @Test
  void loginDevolveJwtERefresh() {
    User u = usuario(7L, (short) 0);
    when(users.findByEmailIgnoreCase("teste@teste.dev")).thenReturn(Optional.of(u));
    when(encoder.matches("senha1234", "bcrypt")).thenReturn(true);
    when(jwtService.issue(u)).thenReturn("jwt-novo");
    when(refreshTokens.emitir(7L)).thenReturn("refresh-cru");

    var resposta = service.login(new AuthDtos.LoginRequest("teste@teste.dev", "senha1234"));

    assertEquals("jwt-novo", resposta.token());
    assertEquals("refresh-cru", resposta.refreshToken());
    assertEquals(7L, resposta.user().id());
  }

  @Test
  void refreshTrocaOPorUmNovoPar() {
    when(refreshTokens.trocar("ref-antigo"))
        .thenReturn(new RefreshTokenService.Renovacao(7L, "ref-novo"));
    User u = usuario(7L, (short) 0);
    when(users.findById(7L)).thenReturn(Optional.of(u));
    when(jwtService.issue(u)).thenReturn("jwt2");

    var resposta = service.refresh(new AuthDtos.RefreshRequest("ref-antigo"));

    assertEquals("jwt2", resposta.token());
    assertEquals("ref-novo", resposta.refreshToken());
    assertEquals(7L, resposta.user().id());
  }

  @Test
  void refreshDeSessaoSemUsuarioE401() {
    when(refreshTokens.trocar("ref-orfao"))
        .thenReturn(new RefreshTokenService.Renovacao(99L, "ref-novo"));
    when(users.findById(99L)).thenReturn(Optional.empty());

    assertThrows(
        InvalidCredentialsException.class,
        () -> service.refresh(new AuthDtos.RefreshRequest("ref-orfao")));
    verify(jwtService, never()).issue(any());
  }
}
