package br.com.favorito.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.favorito.api.config.SecurityConfig;
import br.com.favorito.api.config.TestPropertiesConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verificação de e-mail na camada HTTP: GET /verificar é pública (quem chega é quem tem o token) e
 * POST /reenviar-verificacao responde SEMPRE igual (não revela se o e-mail existe). Falham no
 * código antigo: os dois endpoints nem existiam (404).
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, TestPropertiesConfig.class})
@TestPropertySource(
    properties = {
      "app.cors.allowed-origins=http://localhost:4200",
      "riot.api.key=test-key",
      "riot.api.default-platform=br1"
    })
class VerificacaoEmailControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean AuthService authService;
  @MockitoBean JwtService jwtService;
  @MockitoBean LoginRateLimiter loginLimiter;
  @MockitoBean RegisterRateLimiter registerLimiter;
  @MockitoBean VerificacaoEmailService verificacao;

  @Test
  void verificarTokenValidoRetorna200ComContexto() throws Exception {
    mvc.perform(get("/api/auth/verificar").param("token", "token-raw"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  }

  @Test
  void reenviarRespondeSempre200SemRevelarExistencia() throws Exception {
    mvc.perform(
            post("/api/auth/reenviar-verificacao")
                .contentType("application/json")
                .content("{\"email\":\"naoexiste@exemplo.dev\"}"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  }
}
