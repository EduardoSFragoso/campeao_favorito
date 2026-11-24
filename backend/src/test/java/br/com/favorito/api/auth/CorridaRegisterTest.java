package br.com.favorito.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Corrida no register: entre o existsByEmailIgnoreCase() e o save() outra thread pode inserir o
 * mesmo e-mail. O código não resolve a corrida — é o UNIQUE de users.email do banco que garante 1
 * linha; quem perde toma a violação, convertida em {@link br.com.favorito.api.common.
 * DuplicateResourceException} (409 no HTTP) — antes da correção a violação escapava crua e virava
 * 500 no catch-all. Prova também que o token não é emitido para quem perdeu.
 */
class CorridaRegisterTest {

  @Test
  void quemPerdeACorridaNaoGeraDuplicataNemRecebeToken() {
    UserRepository users = mock(UserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    JwtService jwtService = mock(JwtService.class);
    AuthService service = new AuthService(users, encoder, jwtService);

    // janela da corrida: o exists foi visto ANTES do insert do outro thread
    when(users.existsByEmailIgnoreCase("corrida@exemplo.dev")).thenReturn(false);
    when(encoder.encode(any())).thenReturn("hash");
    User salvo = new User();
    salvo.setId(1L);
    salvo.setEmail("corrida@exemplo.dev");
    // stub duplo: a implementação antiga chamava save(); a nova saveAndFlush() (para a violação
    // de unicidade acontecer DENTRO do método, no escopo do catch)
    when(users.save(any(User.class)))
        .thenReturn(salvo) // 1ª thread vence a corrida
        .thenThrow(new DataIntegrityViolationException("users_email_key")); // 2ª bate no UNIQUE
    when(users.saveAndFlush(any(User.class)))
        .thenReturn(salvo)
        .thenThrow(new DataIntegrityViolationException("users_email_key"));

    var req = new AuthDtos.RegisterRequest("corrida@exemplo.dev", "senha-forte-123", "Ana");

    Assertions.assertDoesNotThrow(() -> service.register(req));
    Assertions.assertThrows(
        br.com.favorito.api.common.DuplicateResourceException.class, () -> service.register(req));

    verify(users, times(2)).saveAndFlush(any(User.class));
    // token só para quem venceu a corrida (1ª paginação do issue)
    verify(jwtService, times(1)).issue(any(User.class));
  }
}
