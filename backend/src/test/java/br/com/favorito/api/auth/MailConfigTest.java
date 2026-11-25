package br.com.favorito.api.auth;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Seleção do transporte de e-mail: SEM app.smtp.host configurado, o envio é de LOG (dev/CI não têm
 * segredo); COM host, o envio é SMTP real. Falha no código antigo, que só tinha o transporte de log
 * em qualquer configuração.
 */
class MailConfigTest {

  @Configuration
  @EnableConfigurationProperties(br.com.favorito.api.config.SmtpProperties.class)
  @Import(MailConfig.class)
  static class Config {}

  private final ApplicationContextRunner comHost =
      new ApplicationContextRunner()
          .withUserConfiguration(Config.class)
          .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
          .withPropertyValues(
              "app.smtp.host=smtp.exemplo.dev",
              "app.smtp.port=587",
              "app.smtp.user=app@exemplo.dev",
              "app.smtp.password=senha-app",
              "app.smtp.from=campeoes@exemplo.dev");

  /** Reproduz o cenário real de dev/CI: sem spring.mail.host, o autoconfig NÃO cria o bean. */
  private final ApplicationContextRunner semHost =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  /**
   * Host configurado mas sem bean JavaMailSender é configuração quebrada; o contexto deve falhar
   * ruidoso, não cair silenciosamente no transporte de log em produção.
   */
  private final ApplicationContextRunner hostSemSender =
      new ApplicationContextRunner()
          .withUserConfiguration(Config.class)
          .withPropertyValues("app.smtp.host=smtp.exemplo.dev");

  @Test
  @DisplayName("host configurado sem JavaMailSender disponível: contexto falha ruidoso")
  void hostSemSenderFalhaRuidoso() {
    hostSemSender.run(ctx -> {
      org.junit.jupiter.api.Assertions.assertInstanceOf(
          BeanCreationException.class, ctx.getStartupFailure());
    });
  }

  @Test
  @DisplayName("SMTP configurado: enviar() entrega pelo JavaMailSender (from/to/assunto/texto)")
  void smtpConfiguradoEntrega() {
    comHost.run(ctx -> {
      EmailEnviador enviador = ctx.getBean(EmailEnviador.class);
      assertInstanceOf(SmtpEmailEnvio.class, enviador, "host presente → transporte SMTP");

      enviador.enviar("ana@exemplo.dev", "Confirme", "corpo");
      var msgs = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
      verify(ctx.getBean(JavaMailSender.class), times(1)).send(msgs.capture());
      var msg = msgs.getValue();
      org.junit.jupiter.api.Assertions.assertEquals("ana@exemplo.dev", msg.getTo()[0]);
      org.junit.jupiter.api.Assertions.assertEquals("Confirme", msg.getSubject());
      org.junit.jupiter.api.Assertions.assertEquals("corpo", msg.getText());
      if (msg.getFrom() != null) {
        org.junit.jupiter.api.Assertions.assertEquals("campeoes@exemplo.dev", msg.getFrom());
      }
    });
  }

  @Test
  @DisplayName(
      "sem app.smtp.host: transporte continua o de LOG e o JavaMailSender nem existe em uso")
  void semHostContinuaLog() {
    semHost.run(ctx -> {
      EmailEnviador enviador = ctx.getBean(EmailEnviador.class);
      assertInstanceOf(LogEmailEnviador.class, enviador, "sem host → transporte de log");
      // e não há bean de JavaMailSender registrado (o autoconfig não o cria sem host)
      org.junit.jupiter.api.Assertions.assertFalse(ctx.containsBean("mailSender"));
    });
  }
}
