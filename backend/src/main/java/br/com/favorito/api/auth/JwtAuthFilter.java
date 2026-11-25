package br.com.favorito.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserRepository users;

  public JwtAuthFilter(JwtService jwtService, UserRepository users) {
    this.jwtService = jwtService;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      String userId = jwtService.validate(token);
      Integer ver = jwtService.versaoDoToken(token);
      // sessão válida só se a versão do token conferir com o banco: logout incrementa token_ver
      // e TODOS os JWT emitidos antes (de qualquer dispositivo) deixam de autenticar.
      boolean sessaoAtual =
          userId != null
              && ver != null
              && users
                  .findById(Long.parseLong(userId))
                  .filter(user -> user.getTokenVer() == ver)
                  .isPresent();
      if (sessaoAtual) {
        var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    }
    filterChain.doFilter(request, response);
  }
}
