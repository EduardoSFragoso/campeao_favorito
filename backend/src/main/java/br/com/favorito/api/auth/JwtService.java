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
    // Guarda em qualquer perfil:
    //  - o placeholder do application.yml só é aceito com o perfil dev ou test EXPLÍCITO
    //    (sprint local — só com warning); sem perfil, prod ou outro, o boot falha; e
    //  - segredo com < 32 bytes é recusado sempre (o HS256 não é seguro — o próprio
    //    io.jsonwebtoken rejeitaria; aqui fecha antes, com mensagem acionável).
    boolean ambienteDeDesenvolvimento = env.matchesProfiles("dev") || env.matchesProfiles("test");
    byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);

    if (SECRET_PLACEHOLDER.equals(secret)) {
      if (!ambienteDeDesenvolvimento) {
        throw new IllegalStateException(
            env.matchesProfiles("prod")
                ? "JWT_SECRET não definida no perfil de produção. "
                    + "Defina JWT_SECRET no EnvironmentFile (openssl rand -base64 64)."
                : "JWT_SECRET não definida: o placeholder do repositório só é aceito "
                    + "nos perfis dev/test. Defina JWT_SECRET no ambiente (openssl rand -base64 64).");
      }
      // dev/test explícito: mantém sessões entre restarts locais, mas NUNCA use em produção
      log.warn(
          "JWT_SECRET não definida: usando placeholder de desenvolvimento (perfil {}). "
              + "Defina uma chave real (openssl rand -base64 64) para produção.",
          String.join(",", env.getActiveProfiles()));
    } else if (bytes.length < 32) {
      // sem exceção de perfil: abaixo de 32 bytes o HS256 não é seguro e o próprio
      // io.jsonwebtoken recusaria (WeakKeyException) — a regra fecha antes, com mensagem
      // acionável, em qualquer perfil
      throw new IllegalStateException(
          "JWT_SECRET muito fraca: "
              + bytes.length
              + " bytes; o mínimo é 32 bytes (HS256)."
              + " Gere com openssl rand -base64 64.");
    }
    this.key = Keys.hmacShaKeyFor(bytes);
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
