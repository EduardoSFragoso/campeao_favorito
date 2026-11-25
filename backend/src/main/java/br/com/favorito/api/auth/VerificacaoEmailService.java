package br.com.favorito.api.auth;

import br.com.favorito.api.common.EntradaInvalidaException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo do token de verificação de e-mail. Token cru (32 bytes, base64url) só existe no e-mail/URL;
 * no banco vai o hash SHA-256. O link montado usa a origem do frontend da produção; em dev o dono
 * chama a API direta ({@code GET /api/auth/verificar?token=...}).
 */
@Service
public class VerificacaoEmailService {

  private static final Logger log = LoggerFactory.getLogger(VerificacaoEmailService.class);

  static final Duration TTL = Duration.ofHours(24);
  static final String ORIGEM_FRONTEND = "https://eduardosfragoso.github.io";

  private final VerificacaoEmailRepository tokens;
  private final UserRepository users;
  private final EmailEnviador email;

  public VerificacaoEmailService(
      VerificacaoEmailRepository tokens, UserRepository users, EmailEnviador email) {
    this.tokens = tokens;
    this.users = users;
    this.email = email;
  }

  /** Cria pedido e envia; falha de envio nunca derruba o cadastro (fica no log). */
  @Transactional
  public void criar(User user) {
    String cru = tokenCru();
    salvarToken(user.getId(), cru);
    try {
      email.enviar(
          user.getEmail(),
          "Campeões Favoritos — confirme seu e-mail",
          "Olá,\n\nConfirme seu cadastro no link abaixo (válido por 24 horas):\n\n"
              + ORIGEM_FRONTEND
              + "/campeao_favorito/verificar?token="
              + cru
              + "\n\nSe você não criou esta conta, ignore este e-mail.\n");
    } catch (RuntimeException e) {
      // sem detalhes do corpo da falha no log do client — o cadastro segue válido
      log.error("Falha ao enviar e-mail de verificação para o usuário {}", user.getId(), e);
    }
  }

  /** Confirma o token e marca o usuário verificado. Token de outro uso/expirado é 400. */
  @Transactional
  public String verificar(String cru) {
    VerificacaoEmail pedido =
        tokens
            .findByTokenHash(TokensOpacos.hash(cru))
            .filter(VerificacaoEmail::utilizavel)
            .orElseThrow(() ->
                new EntradaInvalidaException(
                    "Token de verificação inválido ou expirado — peça um novo."));
    pedido.setUsadoEm(Instant.now());
    tokens.save(pedido);

    User user =
        users
            .findById(pedido.getUserId())
            .orElseThrow(() -> new EntradaInvalidaException("Conta não encontrada."));
    user.setEmailVerificado(true);
    users.save(user);
    return user.getEmail();
  }

  /**
   * Renvio: resposta genérica de fora (`AuthController` decide o HTTP) — mesmo com e-mail
   * inexistente ou já verificado, nada revela; um novo pedido só sai para conta pendente.
   */
  @Transactional
  public void reenviar(String emailDestino) {
    Optional<User> conta = users.findByEmailIgnoreCase(emailDestino);
    if (conta.isEmpty() || conta.get().isEmailVerificado()) {
      log.info("Renvio de verificação sem efeito (conta inexistente ou já confirmada).");
      return;
    }
    criar(conta.get());
  }

  private void salvarToken(Long userId, String cru) {
    VerificacaoEmail pedido = new VerificacaoEmail();
    pedido.setUserId(userId);
    pedido.setTokenHash(TokensOpacos.hash(cru));
    pedido.setExpiraEm(Instant.now().plus(TTL));
    tokens.save(pedido);
  }

  private String tokenCru() {
    return TokensOpacos.novoCru();
  }
}
