package br.com.favorito.api.common;

/** Login de usuário com e-mail ainda não confirmado (403 com mensagem acionável). */
public class EmailNaoVerificadoException extends RuntimeException {
  public EmailNaoVerificadoException() {
    super("Confirme o e-mail antes de entrar.");
  }
}
