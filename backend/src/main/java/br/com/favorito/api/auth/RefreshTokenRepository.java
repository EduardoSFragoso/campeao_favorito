package br.com.favorito.api.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Logout em massa: revoga todos os refreshes ativos do usuário (a sessão do JWT já morre por
   * token_ver).
   */
  @Modifying
  @Query(
      "update RefreshToken r set r.revogadoEm = :agora "
          + "where r.userId = :userId and r.revogadoEm is null")
  void revogarTodosDoUsuario(@Param("userId") long userId, @Param("agora") Instant agora);
}
