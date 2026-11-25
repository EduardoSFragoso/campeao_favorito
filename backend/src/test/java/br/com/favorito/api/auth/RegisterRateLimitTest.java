package br.com.favorito.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.favorito.api.config.SecurityConfig;
import br.com.favorito.api.config.TestPropertiesConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Rate limit do /api/auth/register: 5 tentativas por minuto por IP (a 6ª → 429, mesmo tratamento do
 * login — register também é porta de força bruta/abuso de heap). Cadeia REAL (SecurityConfig +
 * JwtAuthFilter) e o ForwardedHeaderFilter REAL do spring-web (o slice @WebMvcTest não o
 * autoconfigura) para o IP de trás do nginx via X-Forwarded-For.
 */
@WebMvcTest(AuthController.class)
@Import({
  SecurityConfig.class,
  JwtAuthFilter.class,
  TestPropertiesConfig.class,
  LoginRateLimiter.class,
  RegisterRateLimiter.class,
  RegisterRateLimitTest.FiltroDeProducao.class
})
@TestPropertySource(
    properties = {
      "app.cors.allowed-origins=http://localhost:4200",
      "riot.api.key=test-key",
      "riot.api.default-platform=br1",
      "server.forward-headers-strategy=framework"
    })
class RegisterRateLimitTest {

  /** Espelho do que a strategy framework registra no servidor de produção. */
  @TestConfiguration
  static class FiltroDeProducao {
    @Bean
    ForwardedHeaderFilter forwardedHeaderFilter() {
      return new ForwardedHeaderFilter();
    }
  }

  @Autowired MockMvc mvc;

  @MockitoBean AuthService authService;
  @MockitoBean JwtService jwtService; // dependência do JwtAuthFilter real
  @MockitoBean VerificacaoEmailService verificacao;

  private static String corpo(String email) {
    return "{\"email\":\"" + email + "\",\"password\":\"12345678\",\"displayName\":\"Ana\"}";
  }

  private void registraEsperando(String email, String forwardedFor, int status) throws Exception {
    var req = post("/api/auth/register").contentType("application/json").content(corpo(email));
    if (forwardedFor != null) {
      req = req.header("X-Forwarded-For", forwardedFor);
    }
    // o corpo de erro {"error": ...} só existe no 429; no 201 vem o token
    if (status == 201) {
      mvc.perform(req).andExpect(status().is(status));
    } else {
      mvc.perform(req).andExpect(status().is(status)).andExpect(jsonPath("$.error").exists());
    }
  }

  @BeforeEach
  void preparaMock() {
    when(authService.register(any()))
        .thenReturn(
            new AuthDtos.CadastroResponse(
                new AuthDtos.UserResponse(1L, "x@y.dev", "X"), "Verifique o e-mail."));
  }

  @Test
  void cincoRegistrosPassam_eOSextoToma429NoMesmoIp() throws Exception {
    for (int i = 0; i < 5; i++) {
      registraEsperando("u" + i + "@b.dev", null, 201);
    }
    // no código atual a 6ª passava (201) — o limite é novo
    registraEsperando("sexto@b.dev", null, 429);
  }

  @Test
  void outroIpNaoHerdaOBloqueioDoRegistro() throws Exception {
    for (int i = 0; i < 5; i++) {
      registraEsperando("a" + i + "@b.dev", "9.9.9.9", 201);
    }
    registraEsperando("sexto@b.dev", "9.9.9.9", 429);
    registraEsperando("outro@b.dev", "8.8.8.8", 201);
  }
}
