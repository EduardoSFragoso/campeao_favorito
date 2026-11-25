package br.com.favorito.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.favorito.api.common.InvalidCredentialsException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Contrato do refresh token: opaco (só hash no banco), rotação a cada troca, reuso de refresh já
 * revogado derruba a SESSÃO INTEIRA (token_ver++), expirado é só 401.
 */
class RefreshTokenServiceTest {

  private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final RefreshTokenService servico = new RefreshTokenService(tokens, users);

  private RefreshToken refresh(long userId, Instant expiraEm, Instant revogadoEm) {
    RefreshToken r = new RefreshToken();
    r.setUserId(userId);
    r.setTokenHash("hash-qualquer");
    r.setExpiraEm(expiraEm);
    r.setRevogadoEm(revogadoEm);
    return r;
  }

  @Test
  void emitirSalvaSomenteOHashEDevolveOCru() {
    String cru = servico.emitir(42L);

    assertEquals(43, cru.length()); // 32 bytes em base64url sem padding
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(tokens).save(captor.capture());
    RefreshToken salvo = captor.getValue();
    assertNotEquals(cru, salvo.getTokenHash()); // cru NUNCA vai pro banco
    assertEquals(42L, salvo.getUserId());
    // validade ~30 dias (janela larga para não flanquear por milissegundos de relógio)
    double dias = java.time.Duration.between(Instant.now(), salvo.getExpiraEm()).toHours() / 24d;
    assertTrue(dias > 29.9 && dias < 30.1, "validade esperada ~30d, veio " + dias);
  }

  @Test
  void trocarRevogaOAntigoEEmitirUmNovo() {
    RefreshToken antigo = refresh(42L, Instant.now().plusSeconds(3600), null);
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(antigo));

    RefreshTokenService.Renovacao renovacao = servico.trocar("cru-do-antigo");

    assertEquals(42L, renovacao.userId());
    assertEquals(43, renovacao.novoRefreshToken().length());
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(tokens, org.mockito.Mockito.times(2)).save(captor.capture());
    assertEquals(antigo, captor.getAllValues().get(0)); // 1º save: revogação do antigo
    assertTrue(captor.getAllValues().get(0).getRevogadoEm() != null);
    assertTrue(captor.getAllValues().get(1) != antigo); // 2º save: o refresh novo
  }

  @Test
  void reusoDeRefreshJaRevogadoDerrubaASessaoInteira() {
    User dona = new User();
    dona.setId(42L);
    dona.setTokenVer((short) 0);
    RefreshToken antigo = refresh(42L, Instant.now().plusSeconds(3600), Instant.now());
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(antigo));
    when(users.findById(42L)).thenReturn(Optional.of(dona));

    assertThrows(InvalidCredentialsException.class, () -> servico.trocar("cru-do-antigo"));

    assertEquals((short) 1, dona.getTokenVer()); // sessão toda morta no JwtAuthFilter
    verify(users).save(dona);
  }

  @Test
  void refreshExpiradoE401SimplesSemRevogarSessao() {
    RefreshToken morto = refresh(42L, Instant.now().minusSeconds(60), null);
    when(tokens.findByTokenHash(any())).thenReturn(Optional.of(morto));

    assertThrows(InvalidCredentialsException.class, () -> servico.trocar("cru-morto"));

    verify(users, never()).save(any());
  }

  @Test
  void refreshDesconhecidoE401() {
    when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThrows(InvalidCredentialsException.class, () -> servico.trocar("cru-desconhecido"));

    verify(users, never()).save(any());
  }

  @Test
  void revogarTodosMataOsRefreshesDoUsuario() {
    servico.revogarTodosDoUsuario(7L);

    verify(tokens).revogarTodosDoUsuario(eq(7L), any(Instant.class));
  }
}
