package br.com.favorito.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token opaco de sessão/verificação (32 bytes aleatórios, base64url — 43 caracteres): o cru só
 * existe na resposta/e-mail; em qualquer persistência vai apenas o hash SHA-256 (hex, 64 chars).
 * Regra compartilhada por verificação de e-mail e refresh token.
 */
public final class TokensOpacos {

  private static final SecureRandom ALEATORIO = new SecureRandom();

  private TokensOpacos() {}

  /**
   * @return token cru novo: 32 bytes aleatórios em base64url sem padding (43 caracteres)
   */
  public static String novoCru() {
    byte[] bytes = new byte[32];
    ALEATORIO.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * @return o hash SHA-256 do token cru, em hex minúsculo (64 caracteres) — nunca o cru
   */
  public static String hash(String cru) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha.digest(cru.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponível na JVM", e);
    }
  }
}
