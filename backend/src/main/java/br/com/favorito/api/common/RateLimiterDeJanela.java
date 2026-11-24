package br.com.favorito.api.common;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Contador de taxa por janela fixa, em memória — o mesmo padrão do {@code LoginRateLimiter} (origem
 * dele, por dizer), reaproveitado por register e pelas rotas da Riot.
 *
 * <p>N ocorrências por chave na janela; passou do limite, lança 429 com o tempo restante. O mapa
 * tem teto ({@link #TETO_DE_CHAVES}): no estouro saem primeiro as entradas já vencidas e, se ainda
 * faltar, as mais antigas (mesma técnica do conjunto-cache do {@code RiotService}) — sem
 * dependência nova.
 */
public final class RateLimiterDeJanela {

  private static final Logger log = LoggerFactory.getLogger(RateLimiterDeJanela.class);

  /** Teto do mapa de contadores; acima disso, evict (vencidas primeiro, depois as mais antigas). */
  static final int TETO_DE_CHAVES = 10_000;

  /** Fonte de milissegundos (injetável: os testes avançam o tempo sem dormir). */
  public interface Relogio {
    long agora();
  }

  private record Janela(long inicio, int contagem) {}

  private final int limite;
  private final long janelaMs;
  private final String motivo;
  private final Map<String, Janela> chaves = new ConcurrentHashMap<>();
  private final Relogio relogio;

  public RateLimiterDeJanela(int limite, Duration janela, String motivo) {
    this(limite, janela, motivo, System::currentTimeMillis);
  }

  public RateLimiterDeJanela(int limite, Duration janela, String motivo, Relogio relogio) {
    if (limite < 1) {
      throw new IllegalArgumentException("limite tem de ser >= 1");
    }
    this.limite = limite;
    this.janelaMs = janela.toMillis();
    this.motivo = motivo;
    this.relogio = relogio;
  }

  /** Registra uma ocorrência da chave e lança 429 quando ela passa do limite na janela atual. */
  public void checar(String chave) {
    long agora = relogio.agora();
    Janela j =
        chaves.compute(
            chave,
            (k, cur) -> {
              boolean renovaJanela = cur == null || agora - cur.inicio() >= janelaMs;
              return renovaJanela
                  ? new Janela(agora, 1)
                  : new Janela(cur.inicio(), cur.contagem() + 1);
            });
    if (j.contagem() > limite) {
      long retrySec = Math.max(1, (j.inicio() + janelaMs - agora) / 1000);
      // a chave costuma ser IP (LGPD: dado pessoal) — loga mascarado
      log.warn(
          "Rate limit atingido: {} — chave mascarada {} ({}ª ocorrência)",
          motivo,
          mascarar(chave),
          j.contagem());
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, motivo + " Tente de novo em ~" + retrySec + "s.");
    }
    if (chaves.size() > TETO_DE_CHAVES) {
      enxugar(agora);
    }
  }

  /** Mantém o mapa com teto: vencidas saem primeiro; o restante sai o mais antigo. */
  private void enxugar(long agora) {
    chaves.values().removeIf(j -> agora - j.inicio() >= janelaMs);
    while (chaves.size() > TETO_DE_CHAVES) {
      String maisAntiga = null;
      long menor = Long.MAX_VALUE;
      for (var e : chaves.entrySet()) {
        if (e.getValue().inicio() < menor) {
          menor = e.getValue().inicio();
          maisAntiga = e.getKey();
        }
      }
      chaves.remove(maisAntiga);
    }
  }

  /**
   * Mascara chaves para log (LGPD): IPv4 mantém os dois primeiros octetos ("1.2.x.x"); IPv6 e
   * demais, os três primeiros blocos. A chave do mapa segue completa — o limite continua preciso.
   */
  public static String mascarar(String chave) {
    if (chave == null || chave.isBlank()) {
      return "?";
    }
    String[] partes = chave.split("\\.");
    if (partes.length == 4) {
      return partes[0] + "." + partes[1] + ".x.x";
    }
    String[] blocos = chave.split(":");
    return blocos.length > 3 ? blocos[0] + ":" + blocos[1] + ":" + blocos[2] + ":…" : chave;
  }
}
