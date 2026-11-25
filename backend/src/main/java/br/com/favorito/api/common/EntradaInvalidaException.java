package br.com.favorito.api.common;

/**
 * Validação de entrada que NÓS controlamos (platform fora da whitelist, Riot ID inválido, falta de
 * ?platform=): a mensagem é regra nossa, acionável e pode ir INTEIRA para o client (400). Não é
 * para qualquer {@link IllegalArgumentException}: esse continua genérico na resposta (a mensagem de
 * um IAE qualquer pode carregar detalhe interno — só o log).
 */
public class EntradaInvalidaException extends RuntimeException {

  public EntradaInvalidaException(String mensagem) {
    super(mensagem);
  }
}
