package br.com.favorito.api.common;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Leitura do nome da constraint violada a partir da causa de DataIntegrityViolationException — a
 * cadeia real é DIVE → PersistenceException → ConstraintViolationException(Hibernate), então a
 * busca caminha a cadeia inteira.
 */
public final class Unicidade {

  /**
   * Teto defensivo: a causa nunca é cíclica, mas um Throwable torto não pode travar a requisição.
   */
  private static final int PROFUNDIDADE_MAX = 10;

  private Unicidade() {}

  /**
   * Verdadeiro quando, em qualquer nível da cadeia de causas, existe uma {@link
   * ConstraintViolationException} do Hibernate com o nome de constraint esperado. Nome vazado ou
   * cadeia sem a exceção do Hibernate = NÃO é a violação esperada: o chamador rethrow e a resposta
   * segue como erro de servidor, nunca como 409 de duplicata.
   */
  public static boolean violada(Throwable falha, String constraint) {
    Throwable atual = falha;
    for (int profundidade = 0; atual != null && profundidade < PROFUNDIDADE_MAX; profundidade++) {
      if (atual instanceof ConstraintViolationException cve
          && constraint.equals(cve.getConstraintName())) {
        return true;
      }
      atual = atual.getCause();
    }
    return false;
  }
}
