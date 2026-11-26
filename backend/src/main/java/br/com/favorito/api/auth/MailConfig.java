package br.com.favorito.api.auth;

import br.com.favorito.api.config.SmtpProperties;
import java.util.Arrays;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Escolha do transporte de e-mail em tempo de configuração: com {@code app.smtp.host} (= {@code
 * spring.mail.host}) no ambiente é SMTP real; sem, o de log — exceto no perfil {@code prod}, onde o
 * boot FALHA: um transportador de log em produção escreveria o link de verificação (com o token
 * cru) no log e o dono do e-mail jamais receberia nada. O remetente vem pelo autoconfig do mail
 * starter — a entrega só acontece quando o transporte SMTP está selecionado.
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
  public EmailEnviador emailEnviador(
      ObjectProvider<JavaMailSender> remetente, SmtpProperties p, Environment ambiente) {
    if (!p.configurado()) {
      if (Arrays.asList(ambiente.getActiveProfiles()).contains("prod")) {
        // sem valores: os segredos vivem no /etc/campeao-favorito.env da VM, nunca no log/código
        throw new IllegalStateException(
            "MailConfig: SMTP obrigatório no perfil prod. Defina as variáveis SMTP_HOST, "
                + "SMTP_PORT, SMTP_USER, SMTP_PASSWORD e SMTP_FROM no ambiente (.env da VM) — "
                + "sem SMTP_HOST o e-mail de verificação sairia só no log, com o token cru "
                + "exposto e a conta inalcançável por e-mail.");
      }
      return new LogEmailEnviador();
    }
    return new SmtpEmailEnvio(remetente.getObject(), p.from());
  }
}
