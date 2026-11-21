package br.com.favorito.api.auth;

import br.com.favorito.api.common.InvalidCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Resolve o id do usuário logado (subject do JWT). */
@Service
public class CurrentUserService {

  public Long userId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
      throw new InvalidCredentialsException();
    }
    try {
      return Long.parseLong(auth.getName());
    } catch (NumberFormatException e) {
      throw new InvalidCredentialsException();
    }
  }
}
