package br.com.favorito.api.auth;

import br.com.favorito.api.config.SmtpProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Escolha do transporte de e-mail em tempo de configuração: com {@code app.smtp.host} (= {@code
 * spring.mail.host}) no ambiente é SMTP real; sem, o de log (dev/CI e produção ainda não
 * configurada). O remetente vem pelo autoconfig do mail starter — a entrega só acontece quando o
 * transporte SMTP está selecionado.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SmtpProperties.class)
public class MailConfig {

  @Bean
  public EmailEnviador emailEnviador(JavaMailSender remetente, SmtpProperties p) {
    if (!p.configurado()) {
      return new LogEmailEnviador();
    }
    return new SmtpEmailEnvio(remetente, p.from());
  }
}
