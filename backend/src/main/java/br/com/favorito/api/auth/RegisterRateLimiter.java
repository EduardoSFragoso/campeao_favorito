package br.com.favorito.api.auth;

import br.com.favorito.api.common.RateLimiterDeJanela;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Freio no /api/auth/register: 5 tentativas por minuto por IP, mesmo tratamento do login ({@link
 * LoginRateLimiter} é a origem do padrão; o motor é o {@link RateLimiterDeJanela}). Register abusa
 * igual: cria linha no banco e cospe JWT a cada 201.
 */
@Component
public class RegisterRateLimiter {

  static final int MAX_TENTATIVAS = 5;
  static final Duration JANELA = Duration.ofMinutes(1);
  static final String MENSAGEM = "Muitas tentativas de registro.";

  private final RateLimiterDeJanela janela;

  public RegisterRateLimiter() {
    this(System::currentTimeMillis);
  }

  RegisterRateLimiter(RateLimiterDeJanela.Relogio relogio) {
    this.janela = new RateLimiterDeJanela(MAX_TENTATIVAS, JANELA, MENSAGEM, relogio);
  }

  /** Registra a tentativa e lança 429 quando o IP passa do limite na janela atual. */
  public void check(String ip) {
    janela.checar(ip);
  }
}
