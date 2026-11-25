package br.com.favorito.api.auth;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Refresh token da sessão longa: token opaco com rotação. O banco guarda SOMENTE o hash SHA-256 — o
 * token cru existe só na resposta do login/refresh. {@code revogado_em} marca rotação OU logout;
 * reuso de um revogado é sinal de roubo e derruba a sessão inteira.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expira_em", nullable = false)
  private Instant expiraEm;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm = Instant.now();

  @Column(name = "revogado_em")
  private Instant revogadoEm;

  @Transient
  public boolean ativo() {
    return revogadoEm == null && Instant.now().isBefore(expiraEm);
  }
}
