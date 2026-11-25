package br.com.favorito.api.auth;

import br.com.favorito.api.common.InvalidCredentialsException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo do refresh token (sessão longa, opção B): emite na login (30 dias), ROTACIONA a cada troca
 * — o antigo vira revogado — e o REUSO de um revogado é tratado como roubo: derruba a sessão
 * inteira (users.token_ver++, o JwtAuthFilter rejeita todo JWT emitido antes).
 */
@Service
public class RefreshTokenService {

  static final Duration TTL = Duration.ofDays(30);

  private final RefreshTokenRepository tokens;
  private final UserRepository users;

  public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users) {
    this.tokens = tokens;
    this.users = users;
  }

  /** Emite um refresh cru (só a resposta); o banco guarda o hash. */
  @Transactional
  public String emitir(long userId) {
    String cru = TokensOpacos.novoCru();
    RefreshToken refresh = new RefreshToken();
    refresh.setUserId(userId);
    refresh.setTokenHash(TokensOpacos.hash(cru));
    refresh.setExpiraEm(Instant.now().plus(TTL));
    tokens.save(refresh);
    return cru;
  }

  /**
   * Troca um refresh válido por um NOVO par de credenciais de refresh (rotação). Reuso de refresh
   * já revogado → sessão inteira revogada e 401; expirado/desconhecido → 401 simples.
   */
  @Transactional
  public Renovacao trocar(String cru) {
    RefreshToken atual =
        tokens
            .findByTokenHash(TokensOpacos.hash(cru))
            .orElseThrow(InvalidCredentialsException::new);
    if (atual.getRevogadoEm() != null) {
      // Reuso de refresh rotacionado/revogado: possível roubo — mata a sessão inteira
      users
          .findById(atual.getUserId())
          .ifPresent(user -> {
            user.setTokenVer((short) (user.getTokenVer() + 1));
            users.save(user);
          });
      throw new InvalidCredentialsException();
    }
    if (!atual.ativo()) {
      throw new InvalidCredentialsException();
    }
    atual.setRevogadoEm(Instant.now());
    tokens.save(atual);
    return new Renovacao(atual.getUserId(), emitir(atual.getUserId()));
  }

  /** Logout: revoga todos os refreshes do usuário (o JWT morre pelo token_ver++). */
  @Transactional
  public void revogarTodosDoUsuario(long userId) {
    tokens.revogarTodosDoUsuario(userId, Instant.now());
  }

  /** Resultado da rotação: o dono do refresh e o novo token cru. */
  public record Renovacao(long userId, String novoRefreshToken) {}
}
