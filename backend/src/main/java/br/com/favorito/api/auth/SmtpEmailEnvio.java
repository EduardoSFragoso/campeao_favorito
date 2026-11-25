package br.com.favorito.api.auth;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Transporte SMTP real do e-mail de verificação (ativo só com app.smtp.host no ambiente). */
public class SmtpEmailEnvio implements EmailEnviador {

  private final JavaMailSender mail;
  private final String from;

  public SmtpEmailEnvio(JavaMailSender mail, String from) {
    this.mail = mail;
    this.from = from;
  }

  @Override
  public void enviar(String destino, String assunto, String corpo) {
    SimpleMailMessage msg = new SimpleMailMessage();
    if (from != null && !from.isBlank()) {
      msg.setFrom(from);
    }
    msg.setTo(destino);
    msg.setSubject(assunto);
    msg.setText(corpo);
    mail.send(msg);
  }
}
