package br.com.favorito.api.riot;

import br.com.favorito.api.common.RateLimiterDeJanela;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Freio nas rotas /api/riot/**: 30 requisições por minuto POR USUÁRIO autenticado (chave = id do
 * token), 429 ao passar — protege a cota da Riot key (dev: 20 req/s e 100 req/2min) do que o cache
 * por chave já mitiga. Mesmo padrão do {@code LoginRateLimiter}; motor é o {@link
 * RateLimiterDeJanela}.
 */
@Component
public class RiotRateLimiter {

  static final int MAX_REQUISICOES = 30;
  static final Duration JANELA = Duration.ofMinutes(1);
  static final String MENSAGEM = "Muitas requisições para a Riot API.";

  private final RateLimiterDeJanela janela;

  public RiotRateLimiter() {
    this(System::currentTimeMillis);
  }

  RiotRateLimiter(RateLimiterDeJanela.Relogio relogio) {
    this.janela = new RateLimiterDeJanela(MAX_REQUISICOES, JANELA, MENSAGEM, relogio);
  }

  /** Registra a requisição do usuário e lança 429 quando passa do limite na janela atual. */
  public void check(String userId) {
    janela.checar(userId);
  }
}
