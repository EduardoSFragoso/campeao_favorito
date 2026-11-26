package br.com.favorito.api.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificacaoEmailRepository extends JpaRepository<VerificacaoEmail, Long> {

  Optional<VerificacaoEmail> findByTokenHash(String tokenHash);

  /** Último pedido emitido para a conta — base da janela de 5 min do reenvio. */
  Optional<VerificacaoEmail> findTopByUserIdOrderByCriadoEmDesc(Long userId);
}
