package br.com.favorito.api.auth;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pedido de verificação de e-mail: token de uso único com expiração. O banco guarda SOMENTE o hash
 * SHA-256 — o token cru existe no e-mail e na URL de confirmação.
 */
@Entity
@Table(name = "verificacao_email")
@Getter
@Setter
@NoArgsConstructor
public class VerificacaoEmail {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expira_em", nullable = false)
  private Instant expiraEm;

  @Column(name = "usado_em")
  private Instant usadoEm;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm = Instant.now();

  @Transient
  public boolean utilizavel() {
    return usadoEm == null && Instant.now().isBefore(expiraEm);
  }
}
