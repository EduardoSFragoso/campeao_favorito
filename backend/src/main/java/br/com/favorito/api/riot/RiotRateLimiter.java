package br.com.favorito.api.riot;

import br.com.favorito.api.common.RateLimiterDeJanela;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Freio nas rotas /api/riot/**: 10 requisições por minuto POR USUÁRIO autenticado (chave = id do
 * token) MAIS um limite GLOBAL de 40/min para todas as rotas juntos (chave fixa) — protegem a cota
 * da Riot key (dev: 20 req/s e 100 req/2min) do que o cache por chave já mitiga. O limite por
 * usuário é checado primeiro: quem já estourou o próprio não consome vaga do global. Motor é o
 * {@link RateLimiterDeJanela} (mesmo padrão do {@code LoginRateLimiter}).
 */
@Component
public class RiotRateLimiter {

  static final int MAX_REQUISICOES = 10;
  static final int MAX_GLOBAL = 40;
  static final Duration JANELA = Duration.ofMinutes(1);
  static final String MENSAGEM = "Muitas requisições para a Riot API.";

  /** Chave fixa do contador global — todas as requisições /api/riot/** dividem a mesma janela. */
  static final String CHAVE_GLOBAL = "global";

  private final RateLimiterDeJanela porUsuario;
  private final RateLimiterDeJanela global;

  public RiotRateLimiter() {
    this(System::currentTimeMillis);
  }

  RiotRateLimiter(RateLimiterDeJanela.Relogio relogio) {
    this.porUsuario = new RateLimiterDeJanela(MAX_REQUISICOES, JANELA, MENSAGEM, relogio);
    this.global = new RateLimiterDeJanela(MAX_GLOBAL, JANELA, MENSAGEM, relogio);
  }

  /** Registra a requisição e lança 429 quando o limite por usuário OU o global passa na janela. */
  public void check(String userId) {
    porUsuario.checar(userId);
    global.checar(CHAVE_GLOBAL);
  }
}
