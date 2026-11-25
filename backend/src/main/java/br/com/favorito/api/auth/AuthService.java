package br.com.favorito.api.auth;

import br.com.favorito.api.common.EmailNaoVerificadoException;
import br.com.favorito.api.common.InvalidCredentialsException;
import br.com.favorito.api.common.Unicidade;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  /**
   * Resposta ÚNICA do /register (opção A do dono, anti-enumeração): igual para conta nova, pendente
   * e confirmada — ninguém descobre existência de conta pela resposta. Espelhada no
   * CorridaRegisterTest; mudou de um lado, muda do outro.
   */
  static final String MENSAGEM_REGISTER =
      "Se este e-mail for novo, enviamos um link de confirmação. Confira a caixa de entrada.";

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final VerificacaoEmailService verificacao;

  /**
   * SEM @Transactional de propósito: quem perde a corrida em users_email_key toma DIVE numa
   * transação SOBERTANTA (a do próprio INSERT, via repositorio) — se o register abrisse uma
   * transação envolvente, ela ficaria rollback-only e o commit plantaria
   * UnexpectedRollbackException na mão do perdedor (mordeu na CI do PR 85). Consequência aceita:
   * user e token não são atômicos — e o cadastro é resend-covered pelo /reenviar-verificacao se o
   * token não sair.
   */
  public AuthDtos.MensagemResponse register(AuthDtos.RegisterRequest req) {
    String email = req.email().trim().toLowerCase();

    // e-mail existente: MESMA resposta de sempre (nada revelado). Conta pendente aproveita
    // para receber um link novo; conta confirmada não recebe nada — o e-mail decidirá.
    Optional<User> existente = users.findByEmailIgnoreCase(email);
    if (existente.isPresent()) {
      if (!existente.get().isEmailVerificado()) {
        verificacao.criar(existente.get());
      }
      return new AuthDtos.MensagemResponse(MENSAGEM_REGISTER);
    }

    User user = new User();
    user.setEmail(email);
    user.setDisplayName(req.displayName().trim());
    user.setPasswordHash(passwordEncoder.encode(req.password()));
    // saveAndFlush para a violação de unicidade estourar aqui dentro — a corrida no
    // findBy(visto antes do INSERT) é decidida pelo UNIQUE do banco. Quem perde a corrida
    // (users_email_key) recebe a MESMA mensagem genérica; outra violação (FK, CHECK, NOT NULL)
    // escapa e vira 500 no catch-all — responder "duplicata" esconderia o bug. O novo link do
    // perdedor NÃO reenvia aqui: transação abortada; o dono do e-mail reenvia pelo endpoint.
    try {
      user = users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      if (!Unicidade.violada(e, "users_email_key")) {
        throw e;
      }
      return new AuthDtos.MensagemResponse(MENSAGEM_REGISTER);
    }
    // cadastro só vira sessão depois de o dono do e-mail confirmar o token
    verificacao.criar(user);
    return new AuthDtos.MensagemResponse(MENSAGEM_REGISTER);
  }

  public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
    User user =
        users.findByEmailIgnoreCase(req.email()).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    if (!user.isEmailVerificado()) {
      throw new EmailNaoVerificadoException();
    }
    return new AuthDtos.AuthResponse(jwtService.issue(user), AuthDtos.UserResponse.from(user));
  }

  /**
   * Logout de verdade: incrementa a versão da sessão do usuário — todos os JWT emitidos antes
   * (qualquer dispositivo) deixam de autenticar no JwtAuthFilter. Sem lista de sessões: um número
   * por usuário. Nada é emitido de volta; o cliente descarta o próprio token.
   */
  public void logout(long userId) {
    users
        .findById(userId)
        .ifPresent(user -> {
          user.setTokenVer((short) (user.getTokenVer() + 1));
          users.save(user);
        });
  }
}
