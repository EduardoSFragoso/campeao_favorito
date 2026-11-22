package br.com.favorito.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey key;
  private final long expirationMs;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-ms}") long expirationMs,
      Environment env) {
    if (SECRET_PLACEHOLDER.equals(secret)) {
      if (env.matchesProfiles("prod")) {
        // no perfil de produção o segredo tem de vir do ambiente (JWT_SECRET em
        // /etc/campeao-favorito.env); subir com o placeholder público seria aceitar
        // tokens forjados por qualquer um que leia este repositório
        throw new IllegalStateException(
            "JWT_SECRET não definida no perfil de produção. "
                + "Defina JWT_SECRET no EnvironmentFile (openssl rand -base64 64).");
      }
      // aceitável apenas em desenvolvimento local — mantém sessões entre restarts,
      // mas NUNCA use em produção (defina JWT_SECRET com openssl rand -base64 64)
      log.warn(
          "JWT_SECRET não definida: usando placeholder de desenvolvimento. "
              + "Defina uma chave real (openssl rand -base64 64) para produção.");
      this.key = Keys.hmacShaKeyFor(SECRET_PLACEHOLDER.getBytes(StandardCharsets.UTF_8));
    } else {
      this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    this.expirationMs = expirationMs;
  }

  private static final String SECRET_PLACEHOLDER =
      "troque-este-secret-em-producao-com-uma-chave-de-64-bytes-no-minimo!!";
  private static final Logger log = LoggerFactory.getLogger(JwtService.class);

  public String issue(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(user.getId()))
        .claim("email", user.getEmail())
        .claim("displayName", user.getDisplayName())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(expirationMs)))
        .signWith(key)
        .compact();
  }

  /**
   * @return o subject (id do usuário) se o token for válido, senão null
   */
  public String validate(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      return claims.getSubject();
    } catch (JwtException | IllegalArgumentException e) {
      return null;
    }
  }
}
