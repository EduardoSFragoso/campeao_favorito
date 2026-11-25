package br.com.favorito.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Corrida e anti-enumeração no register. Dois contratos:
 *
 * <p>1. O UNIQUE de users.email garante 1 linha na corrida (check-then-insert não resolve); quem
 * perde a corrida em DataIntegrityViolationException(users_email_key) NÃO vira 409 — devolve a
 * MESMA mensagem genérica que quem criou (opção A do dono: o /register não revela e-mail
 * existente). FK/CHECK de outra coisa segue como erro de servidor (500 no catch-all) — responder
 * "duplicata" esconderia o bug.
 *
 * <p>2. E-mail já existente: conta pendente recebe NOVO link de verificação; conta já confirmada
 * não recebe nada — e a resposta HTTP é idêntica nos dois casos (e no da conta nova), então ninguém
 * descobre existência de conta pelo /register.
 */
class CorridaRegisterTest {

  // espelha AuthService.MENSAGEM_REGISTER — se uma das duas mudar sem a outra, quebre AQUI
  private static final String MSG =
      "Se este e-mail for novo, enviamos um link de confirmação. Confira a caixa de entrada.";

  private UserRepository users;
  private PasswordEncoder encoder;
  private JwtService jwtService;
  private VerificacaoEmailService verificacao;
  private AuthService service;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    encoder = mock(PasswordEncoder.class);
    jwtService = mock(JwtService.class);
    verificacao = mock(VerificacaoEmailService.class);
    service =
        new AuthService(users, encoder, jwtService, verificacao, mock(RefreshTokenService.class));
    when(encoder.encode(any())).thenReturn("hash");
  }

  @Test
  @DisplayName("corrida: NINGUÉM toma 409 — quem perde recebe a mesma resposta de quem venceu")
  void quemPerdeACorridaNaoGeraDuplicataNemRecebeToken() {
    UserRepository usersCorrida = mock(UserRepository.class);
    when(usersCorrida.findByEmailIgnoreCase("corrida@exemplo.dev")).thenReturn(Optional.empty());
    User salvo = new User();
    salvo.setId(1L);
    salvo.setEmail("corrida@exemplo.dev");
    // stub duplo: a implementação antiga chamava save(); a nova saveAndFlush() (para a violação
    // de unicidade acontecer DENTRO do método, no escopo do catch)
    when(usersCorrida.save(any(User.class)))
        .thenReturn(salvo) // 1ª thread vence a corrida
        .thenThrow(br.com.favorito.api.support.ViolacaoDeBanco.em("users_email_key"));
    when(usersCorrida.saveAndFlush(any(User.class)))
        .thenReturn(salvo)
        .thenThrow(br.com.favorito.api.support.ViolacaoDeBanco.em("users_email_key"));

    AuthService svc =
        new AuthService(
            usersCorrida,
            encoder,
            jwtService,
            mock(VerificacaoEmailService.class),
            mock(RefreshTokenService.class));

    var req = new AuthDtos.RegisterRequest("corrida@exemplo.dev", "senha-forte-123", "Ana");

    var r1 = Assertions.assertDoesNotThrow(() -> svc.register(req));
    var r2 = Assertions.assertDoesNotThrow(() -> svc.register(req));
    Assertions.assertEquals(r1, r2, "quem perde a corrida recebe a MESMA resposta genérica");

    verify(usersCorrida, times(2)).saveAndFlush(any(User.class));
    // cadastro NÃO emite token (só depois do e-mail confirmado, no login)
    verify(jwtService, never()).issue(any(User.class));
  }

  @Test
  @DisplayName("corrida: perdedor NÃO reenvia link (tx abortada); quem venceu cuida do próprio")
  void perdedorNaoReenviaLink() {
    when(users.findByEmailIgnoreCase("corrida@exemplo.dev")).thenReturn(Optional.empty());
    User salvo = new User();
    salvo.setId(1L);
    when(users.saveAndFlush(any(User.class)))
        .thenReturn(salvo)
        .thenThrow(br.com.favorito.api.support.ViolacaoDeBanco.em("users_email_key"));

    AuthDtos.RegisterRequest req =
        new AuthDtos.RegisterRequest("corrida@exemplo.dev", "senha-forte-123", "Ana");
    service.register(req);
    service.register(req);

    // o criar só aconteceu para quem VENCEU (primeira chamada)
    verify(verificacao, times(1)).criar(any(User.class));
  }

  @Test
  void violacaoDeOutraRestricaoSegueComoErroDeServidor() {
    // FK de outra coisa (ou CHECK etc.): NÃO é duplicata — o "já cadastrado" genérico
    // esconderia um bug; a violação tem de escapar e virar 500 no catch-all
    when(users.saveAndFlush(any(User.class)))
        .thenThrow(br.com.favorito.api.support.ViolacaoDeBanco.em("fk_nao_relacionada"));

    var req = new AuthDtos.RegisterRequest("qualquer@exemplo.dev", "senha-forte-123", "Ana");

    Assertions.assertThrows(DataIntegrityViolationException.class, () -> service.register(req));
  }

  @Test
  @DisplayName("e-mail existente e PENDENTE: resposta genérica + NOVO link para o dono do e-mail")
  void contaPendenteRecebeNovoLinkSemRevelar() {
    User pendente = new User();
    pendente.setId(7L);
    pendente.setEmail("corrida@exemplo.dev");
    when(users.findByEmailIgnoreCase("corrida@exemplo.dev")).thenReturn(Optional.of(pendente));

    var req = new AuthDtos.RegisterRequest("corrida@exemplo.dev", "senha-forte-123", "Ana");
    var resposta = Assertions.assertDoesNotThrow(() -> service.register(req));

    Assertions.assertEquals(MSG, resposta.message());
    verify(verificacao, times(1)).criar(pendente);
    verify(users, never()).saveAndFlush(any(User.class)); // nada novo é criado
  }

  @Test
  @DisplayName("e-mail existente e JÁ VERIFICADO: resposta genérica, NENHUM e-mail novo sai")
  void contaVerificadaNaoRecebeNada() {
    User ok = new User();
    ok.setId(8L);
    ok.setEmail("corrida@exemplo.dev");
    ok.setEmailVerificado(true);
    when(users.findByEmailIgnoreCase("corrida@exemplo.dev")).thenReturn(Optional.of(ok));

    var req = new AuthDtos.RegisterRequest("corrida@exemplo.dev", "senha-forte-123", "Ana");
    var resposta = Assertions.assertDoesNotThrow(() -> service.register(req));

    Assertions.assertEquals(MSG, resposta.message());
    verify(verificacao, never()).criar(any(User.class));
    verify(users, never()).saveAndFlush(any(User.class));
  }
}
