package br.com.favorito.api.auth;

import br.com.favorito.api.common.DuplicateResourceException;
import br.com.favorito.api.common.EmailNaoVerificadoException;
import br.com.favorito.api.common.InvalidCredentialsException;
import br.com.favorito.api.common.Unicidade;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final VerificacaoEmailService verificacao;

  @Transactional
  public AuthDtos.CadastroResponse register(AuthDtos.RegisterRequest req) {
    if (users.existsByEmailIgnoreCase(req.email())) {
      throw new DuplicateResourceException("E-mail já cadastrado.");
    }
    User user = new User();
    user.setEmail(req.email().trim().toLowerCase());
    user.setDisplayName(req.displayName().trim());
    user.setPasswordHash(passwordEncoder.encode(req.password()));
    // saveAndFlush para a violação de unicidade estourar aqui dentro — a corrida no
    // exists(visto antes do INSERT) é decidida pelo UNIQUE do banco. 409 SÓ para o
    // UNIQUE do e-mail (users_email_key, lido na causa): outra violação (FK, CHECK, NOT NULL)
    // escapa e vira 500 no catch-all — responder "E-mail já cadastrado" esconderia o bug.
    try {
      user = users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      if (Unicidade.violada(e, "users_email_key")) {
        throw new DuplicateResourceException("E-mail já cadastrado.");
      }
      throw e;
    }
    // cadastro só vira sessão depois de o dono do e-mail confirmar o token
    verificacao.criar(user);
    return new AuthDtos.CadastroResponse(
        AuthDtos.UserResponse.from(user), "Verifique o e-mail para ativar o acesso.");
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
}
