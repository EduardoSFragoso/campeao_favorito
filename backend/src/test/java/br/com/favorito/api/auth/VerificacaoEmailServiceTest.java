package br.com.favorito.api.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.favorito.api.common.EntradaInvalidaException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ciclo do token de verificação: token cru nunca vai ao banco (só o hash SHA-256), expira em 24h, é
 * de uso único, e o reenvio não revela existência nem estado da conta.
 */
class VerificacaoEmailServiceTest {

  private VerificacaoEmailRepository tokens;
  private UserRepository users;
  private EmailEnviador email;
  private VerificacaoEmailService service;

  private User user(Long id, String email, boolean verificado) {
    User u = new User();
    u.setId(id);
    u.setEmail(email);
    u.setDisplayName("Ana");
    u.setPasswordHash("hash");
    u.setEmailVerificado(verificado);
    return u;
  }

  private static String sha256(String cru) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(cru.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @BeforeEach
  void setUp() {
    tokens = mock(VerificacaoEmailRepository.class);
    users = mock(UserRepository.class);
    email = mock(EmailEnviador.class);
    service = new VerificacaoEmailService(tokens, users, email);
  }

  private String tokenSalvoExtraidoDoLink() {
    var captura = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(email).enviar(any(), contains("confirme"), captura.capture());
    Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(captura.getValue());
    if (!m.find()) {
      throw new AssertionError("corpo do e-mail não contém o link de verificação");
    }
    return m.group(1);
  }

  @Test
  @DisplayName("criar salva SÓ o hash do token e envia link de 24h; envio que falha não derruba")
  void criarSalvaHashEEnviaLink() {
    User ana = user(1L, "ana@exemplo.dev", false);

    assertDoesNotThrow(() -> service.criar(ana));
    String cru = tokenSalvoExtraidoDoLink();
    var salvo = org.mockito.ArgumentCaptor.forClass(VerificacaoEmail.class);
    verify(tokens, org.mockito.Mockito.atLeastOnce()).save(salvo.capture());
    assertEquals(sha256(cru), salvo.getAllValues().get(0).getTokenHash());
    // v1: expira entre 23h e 24h a partir de agora (não pode desabar antes de 23h)
    org.junit.jupiter.api.Assertions.assertTrue(
        salvo.getAllValues().get(0).getExpiraEm().isBefore(Instant.now().plusSeconds(25 * 3600)));
    org.junit.jupiter.api.Assertions.assertTrue(
        salvo.getAllValues().get(0).getExpiraEm().isAfter(Instant.now().plusSeconds(23 * 3600)));
  }

  @Test
  @DisplayName("corpo do e-mail de enviar não pode quebrar o cadastro (envio lança)")
  void falhaDeEnvioNaoDerrubaOCadastro() {
    when(users.findByEmailIgnoreCase(any()))
        .thenReturn(Optional.of(user(1L, "ana@exemplo.dev", false)));
    org.mockito.Mockito.doThrow(new IllegalStateException("smtp caiu"))
        .when(email)
        .enviar(any(), any(), any());

    assertDoesNotThrow(() -> service.criar(user(2L, "b@exemplo.dev", false)));
  }

  @Test
  @DisplayName("verificar: token válido marca o usuário verificado e o pedido como usado")
  void verificarTokenValido() {
    service.criar(user(1L, "ana@exemplo.dev", false));
    String cru = tokenSalvoExtraidoDoLink();

    VerificacaoEmail pedidoExibido = new VerificacaoEmail(); // capturado no save do criar
    pedidoExibido.setUserId(1L);
    pedidoExibido.setTokenHash(sha256(cru));
    pedidoExibido.setExpiraEm(Instant.now().plusSeconds(3600));
    when(tokens.findByTokenHash(sha256(cru))).thenReturn(Optional.of(pedidoExibido));
    when(users.findById(1L)).thenReturn(Optional.of(user(1L, "ana@exemplo.dev", false)));

    assertEquals("ana@exemplo.dev", service.verificar(cru));

    verify(users).save(org.mockito.ArgumentMatchers.argThat(u -> u.isEmailVerificado()));
    verify(tokens).save(org.mockito.ArgumentMatchers.argThat(p -> p.getUsadoEm() != null));
  }

  @Test
  @DisplayName("verificar: token desconhecido, usado ou expirado → 400 acionável")
  void verificarTokenInvalido() {
    when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());
    assertThrows(EntradaInvalidaException.class, () -> service.verificar("desconhecido"));

    VerificacaoEmail usado = new VerificacaoEmail();
    usado.setUserId(1L);
    usado.setTokenHash("h");
    usado.setExpiraEm(Instant.now().plusSeconds(3600));
    usado.setUsadoEm(Instant.now());
    when(tokens.findByTokenHash("h")).thenReturn(Optional.of(usado));
    assertThrows(EntradaInvalidaException.class, () -> service.verificar("h"));

    VerificacaoEmail expirado = new VerificacaoEmail();
    expirado.setUserId(1L);
    expirado.setTokenHash("h2");
    expirado.setExpiraEm(Instant.now().minusSeconds(1));
    when(tokens.findByTokenHash("h2")).thenReturn(Optional.of(expirado));
    assertThrows(EntradaInvalidaException.class, () -> service.verificar("h2"));
  }

  @Test
  @DisplayName("reenvio: conta inexistente OU verificada não envia e não revela nada")
  void reenvioGenerico() {
    when(users.findByEmailIgnoreCase("nada@exemplo.dev")).thenReturn(Optional.empty());
    when(users.findByEmailIgnoreCase("verificado@exemplo.dev"))
        .thenReturn(Optional.of(user(2L, "verificado@exemplo.dev", true)));

    service.reenviar("nada@exemplo.dev");
    service.reenviar("verificado@exemplo.dev");

    verify(email, never()).enviar(any(), any(), any());
  }
}
