package br.com.favorito.api.auth;

import br.com.favorito.api.common.RateLimiterDeJanela;
import java.time.Duration;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/**
 * Freio de força bruta no /api/auth/login: N tentativas por janela fixa por IP (o IP real vem do
 * X-Forwarded-For do nginx, convertido pelo forward-headers-strategy; sem nginx, é o remoteAddr).
 * Em memória de propósito — um app de um servidor não justifica Redis para isso. A lógica de
 * janela/teto vive no {@link RateLimiterDeJanela} (compartilhada com register e Riot).
 */
@Component
public class LoginRateLimiter {

  static final int MAX_ATTEMPTS = 10;
  static final Duration WINDOW = Duration.ofMinutes(1);
  private static final String MENSAGEM = "Muitas tentativas de login.";

  private final RateLimiterDeJanela janela;

  public LoginRateLimiter() {
    this(System::currentTimeMillis);
  }

  LoginRateLimiter(LongSupplier clock) {
    this.janela = new RateLimiterDeJanela(MAX_ATTEMPTS, WINDOW, MENSAGEM, clock::getAsLong);
  }

  /** Registra uma tentativa de login e lança 429 quando o IP passa do limite na janela atual. */
  public void check(String ip) {
    janela.checar(ip);
  }

  /** Mascaramento de IP nos logs (LGPD) — delegado ao núcleo compartilhado. */
  static String mask(String ip) {
    return RateLimiterDeJanela.mascarar(ip);
  }
}
