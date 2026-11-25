package br.com.favorito.api.auth;

/**
 * Transporte de e-mail. O cadastro NÃO pode falhar nem vazar detalhes se o envio falhar
 * (implementação decide: log com erro acionável). PR 2 traz a implementação SMTP real — sem {@code
 * SMTP_HOST} no ambiente, o padrão é a de log.
 */
public interface EmailEnviador {

  /**
   * @param destino endereço do destinatário
   * @param assunto assunto em texto plano
   * @param corpo corpo em texto (texto plano — cadastro é acionável sem HTML)
   */
  void enviar(String destino, String assunto, String corpo);
}
