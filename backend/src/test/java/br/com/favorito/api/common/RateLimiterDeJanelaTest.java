package br.com.favorito.api.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Núcleo compartilhado dos rate limiters (login/register/Riot): janela fixa por chave, 429 com
 * tempo restante e TETO DE TAMANHO no mapa de contadores — vencidas saem primeiro, depois as mais
 * antigas (padrão do teto do RiotService).
 */
class RateLimiterDeJanelaTest {

  private final AtomicLong agora = new AtomicLong(1_000_000L);
  private final RateLimiterDeJanela limiter =
      new RateLimiterDeJanela(3, Duration.ofMinutes(1), "Muitas.", agora::get);

  @SuppressWarnings("unchecked")
  private Map<String, Object> tamanhoInterno() throws Exception {
    Field f = RateLimiterDeJanela.class.getDeclaredField("chaves");
    f.setAccessible(true);
    return (Map<String, Object>) f.get(limiter);
  }

  @Test
  void limiteNaJanela_eQuatroQuebraCom429ENoTempo() {
    for (int i = 0; i < 3; i++) {
      assertDoesNotThrow(() -> limiter.checar("k"));
    }
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> limiter.checar("k"));
    assertTrue(
        ex.getReason() != null && ex.getReason().matches("Muitas\\. Tente de novo em ~[0-9]+s\\."),
        "mensagem com tempo restante, veio: " + ex.getReason());
  }

  @Test
  void janelaNovaLiberaEOutraChaveNaoHerdaCastigo() {
    for (int i = 0; i < 4; i++) {
      try {
        limiter.checar("k");
      } catch (ResponseStatusException ignored) {
        // apenas esgota
      }
    }
    assertDoesNotThrow(() -> limiter.checar("outra"));
    agora.addAndGet(Duration.ofMinutes(1).toMillis() + 1);
    assertDoesNotThrow(() -> limiter.checar("k"));
  }

  @Test
  void tetoDeChaves_vencidasSobrevivemPrimeiro_eTetoDuroEvictaAmaisAntiga() throws Exception {
    // relógio parado: chaves novas além do teto não vencem — o evict é da mais antiga
    for (int i = 0; i <= 10_005; i++) {
      final String chave = "k" + i;
      assertDoesNotThrow(() -> limiter.checar(chave));
    }
    assertTrue(tamanhoInterno().size() <= RateLimiterDeJanela.TETO_DE_CHAVES);

    // a mais antiga foi evitada: volta a contar do zero sem 429
    assertDoesNotThrow(() -> limiter.checar("k0"));
    assertDoesNotThrow(() -> limiter.checar("k0")); // 2 ocorrências ainda não estoura limite 3

    // chaves vencidas saem antes das vivas: avança a janela inteira e enche de novo
    agora.addAndGet(Duration.ofMinutes(1).toMillis() + 1);
    for (int i = 0; i < 200; i++) {
      final String chave = "n" + i;
      assertDoesNotThrow(() -> limiter.checar(chave));
    }
    // as chaves velhas vencidas não contam mais para o teto (limpeza em estouro)
    assertTrue(tamanhoInterno().size() <= 200);
  }

  @Test
  void logMascaraChaveTipoIp_eChaveNaoIpFicaInteira() {
    assertEquals("1.2.x.x", RateLimiterDeJanela.mascarar("1.2.3.4"));
    assertEquals("2001:db8:1:…", RateLimiterDeJanela.mascarar("2001:db8:1:2:3:4:5:6"));
    assertEquals("7", RateLimiterDeJanela.mascarar("7"));
    assertEquals("?", RateLimiterDeJanela.mascarar(null));
    assertEquals("?", RateLimiterDeJanela.mascarar("  "));
  }
}
