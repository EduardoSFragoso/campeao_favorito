package br.com.favorito.api.auth;

import br.com.favorito.api.config.SmtpProperties;
import org.springframework.beans.factory.ObjectProvider;
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

  /**
   * A resolução do {@code JavaMailSender} é opcional de propósito: sem {@code spring.mail.host}, o
   * autoconfig do mail não cria o bean e a injeção obrigatória derrubaria todo o contexto (falha
   * comprovada na CI do PR 81). Host configurado sem sender disponível segue sendo erro ruidoso.
   */
  @Bean
  public EmailEnviador emailEnviador(ObjectProvider<JavaMailSender> remetente, SmtpProperties p) {
    if (!p.configurado()) {
      return new LogEmailEnviador();
    }
    return new SmtpEmailEnvio(remetente.getObject(), p.from());
  }
}
