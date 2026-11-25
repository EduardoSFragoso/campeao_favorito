package br.com.favorito.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Espelho de {@code spring.mail.host} + remetente, para o {@code MailConfig} decidir o transporte:
 * host vazio → e-mail de log (dev/CI nada sai); host presente → SMTP real. Credenciais e timeouts
 * ficam em {@code spring.mail.*}/application.yml, preenchidos por env no EnvironmentFile da VM
 * (nomes em .env.example).
 */
@ConfigurationProperties(prefix = "app.smtp")
public record SmtpProperties(String host, String from) {

  public boolean configurado() {
    return host != null && !host.isBlank();
  }
}
