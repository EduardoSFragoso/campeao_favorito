package br.com.favorito.api.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificacaoEmailRepository extends JpaRepository<VerificacaoEmail, Long> {

  Optional<VerificacaoEmail> findByTokenHash(String tokenHash);
}
