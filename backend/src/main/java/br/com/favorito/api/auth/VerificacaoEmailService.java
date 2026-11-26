package br.com.favorito.api.auth;

import br.com.favorito.api.common.EntradaInvalidaException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

  /**
   * Janela mínima entre dois pedidos de e-mail para a MESMA conta — register (conta pendente) e
   * reenvio podem ser chamados em rajada; sem isso, alguém enche a caixa de entrada de terceiros de
   * links (o gasto SMTP é por conta dele). Resposta HTTP dos endpoints NÃO muda: continua a
   * genérica de sempre.
   */
  static final Duration JANELA_REENVIO = Duration.ofMinutes(5);

  private final VerificacaoEmailRepository tokens;
  private final UserRepository users;
  private final EmailEnviador email;

  /** Onde o envio desacoplado roda (produção: fila curta com rejeição silenciosa e logada). */
  private final java.util.concurrent.Executor emailExecutor;

  private final SecureRandom aleatorio = new SecureRandom();

  public VerificacaoEmailService(
      VerificacaoEmailRepository tokens,
      UserRepository users,
      EmailEnviador email,
      java.util.concurrent.Executor emailExecutor) {
    this.tokens = tokens;
    this.users = users;
    this.email = email;
    this.emailExecutor = emailExecutor;
  }

  /** Grava o token (transacional) e agenda o envio para DEPOIS do commit, em thread própria. */
  @Transactional
  public void criar(User user) {
    if (janelaEmVigor(user.getId())) {
      // silencioso e sem erro: o HTTP continua a mesma resposta genérica (não revela nada)
      log.info(
          "Reenvio para a conta {} dentro da janela de {} min — e-mail novo NÃO emitido.",
          user.getId(),
          JANELA_REENVIO.toMinutes());
      return;
    }
    String cru = tokenCru();
    salvarToken(user.getId(), cru);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              dispararEnvio(user.getEmail(), cru);
            }
          });
    } else {
      // fora de transação (não há commit a esperar): manda já, ainda assim assíncrono
      dispararEnvio(user.getEmail(), cru);
    }
  }

  /** Envia em thread própria; SMTP lento/caído NUNCA segura nem derruba a resposta HTTP. */
  private void dispararEnvio(String destino, String cru) {
    emailExecutor.execute(() -> {
      try {
        email.enviar(
            destino,
            "Campeões Favoritos — confirme seu e-mail",
            "Olá,\n\nConfirme seu cadastro no link abaixo (válido por 24 horas):\n\n"
                + ORIGEM_FRONTEND
                + "/campeao_favorito/verificar?token="
                + cru
                + "\n\nSe você não criou esta conta, ignore este e-mail.\n");
      } catch (RuntimeException e) {
        // sem detalhes do corpo da falha no log do client — o token gravado segue válido por 24h
        log.error("Falha ao enviar e-mail de verificação para {}", destino, e);
      }
    });
  }

  /** Confirma o token e marca o usuário verificado. Token de outro uso/expirado é 400. */
  @Transactional
  public String verificar(String cru) {
    VerificacaoEmail pedido =
        tokens
            .findByTokenHash(hash(cru))
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

  /** True quando já houve um pedido para a conta dentro da janela de 5 min. */
  private boolean janelaEmVigor(Long userId) {
    return tokens
        .findTopByUserIdOrderByCriadoEmDesc(userId)
        .map(VerificacaoEmail::getCriadoEm)
        .map(criadoEm -> criadoEm.isAfter(Instant.now().minus(JANELA_REENVIO)))
        .orElse(false);
  }

  private void salvarToken(Long userId, String cru) {
    VerificacaoEmail pedido = new VerificacaoEmail();
    pedido.setUserId(userId);
    pedido.setTokenHash(hash(cru));
    pedido.setExpiraEm(Instant.now().plus(TTL));
    tokens.save(pedido);
  }

  private String tokenCru() {
    byte[] bytes = new byte[32];
    aleatorio.nextBytes(bytes);
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String cru) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha.digest(cru.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponível na JVM", e);
    }
  }
}
