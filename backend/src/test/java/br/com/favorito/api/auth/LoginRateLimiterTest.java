package br.com.favorito.api.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Janela fixa por IP: 10 tentativas/min, 11ª → 429 com tempo restante; IPs independentes. */
class LoginRateLimiterTest {

  private final AtomicLong now = new AtomicLong(1_000_000L);
  private final LoginRateLimiter limiter = new LoginRateLimiter(now::get);

  @Test
  void dezTentativasPassam_eAOnocausta429() {
    for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
      assertDoesNotThrow(() -> limiter.check("1.2.3.4"));
    }
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> limiter.check("1.2.3.4"));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
  }

  @Test
  void janelaNovaLiberaDeNovo() {
    for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
      assertDoesNotThrow(() -> limiter.check("1.2.3.4"));
    }
    assertThrows(ResponseStatusException.class, () -> limiter.check("1.2.3.4"));
    now.addAndGet(LoginRateLimiter.WINDOW.toMillis() + 1);
    assertDoesNotThrow(() -> limiter.check("1.2.3.4"));
  }

  @Test
  void ipIndependenteNaoHerdaOCastigo() {
    for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
      assertDoesNotThrow(() -> limiter.check("1.2.3.4"));
    }
    assertDoesNotThrow(() -> limiter.check("5.6.7.8"));
  }

  @Test
  void mensagem429TrazTempoRestanteEmSegundos() {
    for (int i = 0; i <= LoginRateLimiter.MAX_ATTEMPTS; i++) {
      try {
        limiter.check("1.2.3.4");
      } catch (ResponseStatusException ignored) {
        // apenas esgota a janela
      }
    }
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> limiter.check("1.2.3.4"));
    assertTrue(
        ex.getReason() != null && ex.getReason().matches(".*[0-9]+s\\."),
        "esperado 'Tente de novo em ~Ns', veio: " + ex.getReason());
  }

  @Test
  void logMascaraOIp() {
    assertEquals("1.2.x.x", LoginRateLimiter.mask("1.2.3.4"));
    assertEquals("2001:db8:1:…", LoginRateLimiter.mask("2001:db8:1:2:3:4:5:6"));
    assertEquals("?", LoginRateLimiter.mask(null));
    assertEquals("?", LoginRateLimiter.mask("  "));
  }
}
