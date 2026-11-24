package br.com.favorito.api.support;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Fault factory: DataIntegrityViolationException COMO o Hibernate/JPA a produz de verdade — causa
 * PersistenceException envolvendo a ConstraintViolationException do Hibernate com o nome da
 * constraint violada. Sem essa cadeia, os services não distinguem o UNIQUE esperado de outra
 * violação (FK, CHECK, NOT NULL).
 */
public final class ViolacaoDeBanco {

  private ViolacaoDeBanco() {}

  public static DataIntegrityViolationException em(String constraint) {
    return new DataIntegrityViolationException(
        "violação de integridade",
        new jakarta.persistence.PersistenceException(
            new ConstraintViolationException(
                "insert into ...", new SQLException("violação de constraint"), constraint)));
  }
}
