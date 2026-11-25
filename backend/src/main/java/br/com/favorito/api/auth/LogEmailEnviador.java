package br.com.favorito.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Transporte de log: o link de verificação sai no log da aplicação. Serve para dev/CI e para
 * produção até o envio SMTP real ser ligado (PR 2). Enquanto for o transporte em produção, o dono
 * entrega o link manualmente. Sem segredos: o link carrega token de uso único.
 */
@Component
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
