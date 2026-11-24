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
 * Caracterização da corrida no register: entre o existsByEmailIgnoreCase() e o save() outra thread
 * pode inserir o mesmo e-mail. O código não resolve a corrida — é o UNIQUE de users.email do banco
 * que garante 1 linha; quem perde toma a exceção de integridade SEM tratamento (hoje vira 500 no
 * HTTP via catch-all). Prova também que o token não é emitido para quem perdeu.
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
    when(users.save(any(User.class)))
        .thenReturn(salvo) // 1ª thread vence a corrida
        .thenThrow(new DataIntegrityViolationException("users_email_key")); // 2ª bate no UNIQUE

    var req = new AuthDtos.RegisterRequest("corrida@exemplo.dev", "senha-forte-123", "Ana");

    Assertions.assertDoesNotThrow(() -> service.register(req));
    Assertions.assertThrows(DataIntegrityViolationException.class, () -> service.register(req));

    verify(users, times(2)).save(any(User.class));
    // token só para quem venceu a corrida (1ª páginação do issue)
    verify(jwtService, times(1)).issue(any(User.class));
  }
}
