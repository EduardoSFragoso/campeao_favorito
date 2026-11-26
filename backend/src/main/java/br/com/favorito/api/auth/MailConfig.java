package br.com.favorito.api.auth;

import br.com.favorito.api.config.SmtpProperties;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Escolha do transporte de e-mail em tempo de configuração: com {@code app.smtp.*} completo (host,
 * from, user, password) é SMTP real; incompleto, o de log — exceto no perfil {@code prod}, onde o
 * boot FALHA: um transportador de log em produção escreveria o link de verificação (com o token
 * cru) no log e o dono do e-mail jamais receberia nada. Configuração incompleta em prod falha no
 * boot, não no primeiro envio.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SmtpProperties.class)
public class MailConfig {

  private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

  /**
   * A resolução do {@code JavaMailSender} é opcional de propósito: sem {@code spring.mail.host}, o
   * autoconfig do mail não cria o bean e a injeção obrigatória derrubaria todo o contexto (falha
   * comprovada na CI do PR 81). Configuração completa SEM sender disponível segue sendo erro
   * ruidoso.
   */
  @Bean
  public EmailEnviador emailEnviador(
      ObjectProvider<JavaMailSender> remetente, SmtpProperties p, Environment ambiente) {
    if (!p.configurado()) {
      if (Arrays.asList(ambiente.getActiveProfiles()).contains("prod")) {
        // sem valores: os segredos vivem no /etc/campeao-favorito.env da VM, nunca no log/código
        throw new IllegalStateException(
            "MailConfig: SMTP incompleto e obrigatório no perfil prod. Defina TODAS as variáveis "
                + "SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD e SMTP_FROM no ambiente "
                + "(.env da VM) — transporte parcial ou de log deixaria o e-mail de verificação "
                + "(com token cru) sem sair ou indo só ao log.");
      }
      return new LogEmailEnviador();
    }
    return new SmtpEmailEnvio(remetente.getObject(), p.from());
  }

  /**
   * Fila CURTA de envio (fora do caminho HTTP): 1 thread, 6 na fila. Fila cheia = rejeição
   * silenciosa e LOGADA (a resposta HTTP já voltou; o dono do e-mail reenvia pela API). Sem
   * {@code @EnableAsync} para não contaminar todo o app: só este executor é assíncrono.
   */
  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor emailExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("email-envio-");
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(6);
    executor.setRejectedExecutionHandler((tarefa, filas) ->
        log.error("Fila de e-mail cheia (6) — envio de verificação DESCARTADO (reenviar)."));
    return executor;
  }
}
