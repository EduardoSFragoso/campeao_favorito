package br.com.favorito.api.auth;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  @Email
  @NotBlank
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String displayName;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  /** Cadastro confirmado: é ligado quando o dono do e-mail valida o token de verificação. */
  @Column(name = "email_verificado", nullable = false)
  private boolean emailVerificado = false;

  /**
   * Versão da sessão: o JwtAuthFilter só autentica JWT cujo claim "ver" seja IGUAL a este valor. O
   * logout incrementa — matando todos os tokens emitidos antes (sem lista de sessões).
   */
  @Column(name = "token_ver", nullable = false)
  private short tokenVer = 0;
}
