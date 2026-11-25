package br.com.favorito.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transporte de log: o link de verificação sai no log da aplicação. Serve para dev/CI e para
 * produção até o envio SMTP real ser ligado (app.smtp.host no ambiente). Criado pelo {@link
 * MailConfig} — sem anotação de bean própria para não duplicar o EmailEnviador.
 */
public class LogEmailEnviador implements EmailEnviador {

  private static final Logger log = LoggerFactory.getLogger(LogEmailEnviador.class);

  @Override
  public void enviar(String destino, String assunto, String corpo) {
    log.info("EMAIL [para={}] assunto={}: {}", mascara(destino), assunto, corpo);
  }

  private String mascara(String email) {
    int arroba = email.indexOf('@');
    if (arroba <= 1) {
      return "***" + (arroba < 0 ? "" : email.substring(arroba));
    }
    return email.charAt(0) + "***" + email.substring(arroba);
  }
}
