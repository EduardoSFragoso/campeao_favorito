package br.com.favorito.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Espelho de {@code spring.mail.*} para o {@code MailConfig} decidir o transporte. Completo = host,
 * from, user e password — os 4 preenchidos (via env: SMTP_HOST/SMTP_FROM/SMTP_USER/ SMTP_PASSWORD).
 * Faltando qualquer um, o transporte NÃO é SMTP: em dev/test/CI é o de log; em prod o boot falha
 * (ver MailConfig). Credenciais e timeouts ficam em {@code spring.mail.*}/ application.yml,
 * preenchidos por env no EnvironmentFile da VM (nomes em .env.example).
 */
@ConfigurationProperties(prefix = "app.smtp")
public record SmtpProperties(String host, String from, String user, String password) {

  /**
   * @return true só com host, from, user e password TODOS preenchidos.
   */
  public boolean configurado() {
    return preenchido(host) && preenchido(from) && preenchido(user) && preenchido(password);
  }

  private static boolean preenchido(String valor) {
    return valor != null && !valor.isBlank();
  }
}
