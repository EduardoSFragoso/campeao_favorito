package br.com.favorito.api.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** O logout REAL: incrementar token_ver mata todo JWT emitido antes (sessões não têm lista). */
class AuthServiceLogoutTest {

  private final UserRepository users = mock(UserRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final JwtService jwtService = mock(JwtService.class);
  private final VerificacaoEmailService verificacao = mock(VerificacaoEmailService.class);
  private final AuthService service = new AuthService(users, encoder, jwtService, verificacao);

  private User usuario(long id, short tokenVer) {
    User u = new User();
    u.setId(id);
    u.setTokenVer(tokenVer);
    return u;
  }

  @Test
  void logoutIncrementaAVersaoESalva() {
    User u = usuario(7L, (short) 2);
    when(users.findById(7L)).thenReturn(Optional.of(u));

    service.logout(7L);

    assertEquals((short) 3, u.getTokenVer());
    verify(users).save(u);
  }

  @Test
  void logoutDeUsuarioInexistenteNaoFalha() {
    when(users.findById(99L)).thenReturn(Optional.empty());

    assertDoesNotThrow(() -> service.logout(99L));
    verify(users, never()).save(any(User.class));
  }
}
