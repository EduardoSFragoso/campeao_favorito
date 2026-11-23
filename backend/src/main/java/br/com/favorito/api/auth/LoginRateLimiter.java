package br.com.favorito.api.auth;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Freio de força bruta no /api/auth/login: N tentativas por janela fixa por IP (o IP real vem do
 * X-Forwarded-For do nginx, convertido pelo forward-headers-strategy; sem nginx, é o remoteAddr).
 * Em memória de propósito — um app de um servidor não justifica Redis para isso.
 */
@Component
public class LoginRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

  static final int MAX_ATTEMPTS = 10;
  static final Duration WINDOW = Duration.ofMinutes(1);
  private static final int CLEAN_UP_THRESHOLD = 10_000;

  private final Map<String, Window> hits = new ConcurrentHashMap<>();
  private final LongSupplier clock;

  /** Fonte de milissegundos (injetável: os testes avançam o tempo sem dormir). */
  public interface LongSupplier {
    long now();
  }

  private record Window(long start, int count) {}

  public LoginRateLimiter() {
    this(System::currentTimeMillis);
  }

  LoginRateLimiter(LongSupplier clock) {
    this.clock = clock;
  }

  /** Registra uma tentativa de login e lança 429 quando o IP passa do limite na janela atual. */
  public void check(String ip) {
    long now = clock.now();
    Window w =
        hits.compute(
            ip,
            (k, cur) -> {
              boolean renewWindow = cur == null || now - cur.start >= WINDOW.toMillis();
              return renewWindow ? new Window(now, 1) : new Window(cur.start, cur.count + 1);
            });
    if (w.count() > MAX_ATTEMPTS) {
      long retrySec = Math.max(1, (w.start() + WINDOW.toMillis() - now) / 1000);
      // LGPD: IP é dado pessoal — loga mascarado (chave real do map segue intacta)
      log.warn("Rate limit de login atingido para IP {} ({} tentativas)", mask(ip), w.count());
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS,
          "Muitas tentativas de login. Tente de novo em ~" + retrySec + "s.");
    }
    if (hits.size() > CLEAN_UP_THRESHOLD) {
      hits.entrySet().removeIf(e -> now - e.getValue().start() >= WINDOW.toMillis());
    }
  }

  /**
   * Mascara o IP para log (LGPD): IPv4 mantém os dois primeiros octetos ("1.2.x.x"); IPv6 e demais,
   * os três primeiros blocos. A chave do mapa segue com o IP completo — o limite por IP continua
   * preciso; só o log que não carrega o dado pessoal inteiro.
   */
  static String mask(String ip) {
    if (ip == null || ip.isBlank()) {
      return "?";
    }
    String[] parts = ip.split("\\.");
    if (parts.length == 4) {
      return parts[0] + "." + parts[1] + ".x.x";
    }
    String[] blocks = ip.split(":");
    return blocks.length > 3 ? blocks[0] + ":" + blocks[1] + ":" + blocks[2] + ":…" : ip;
  }
}
