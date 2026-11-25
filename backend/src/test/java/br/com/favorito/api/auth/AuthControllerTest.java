package br.com.favorito.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.favorito.api.common.InvalidCredentialsException;
import br.com.favorito.api.config.SecurityConfig;
import br.com.favorito.api.config.TestPropertiesConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Camada HTTP do auth: validação de corpo, rate limit virando 429 e a cadeia de segurança REAL
 * (SecurityConfig + JwtAuthFilter importados de verdade; só os serviços são mockados). CSRF está
 * desligado na cadeia real, então os POSTs vão sem post processor.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, TestPropertiesConfig.class})
@TestPropertySource(
    properties = {
      "app.cors.allowed-origins=http://localhost:4200",
      "riot.api.key=test-key",
      "riot.api.default-platform=br1"
    })
class AuthControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean AuthService authService;
  @MockitoBean JwtService jwtService; // dependência do JwtAuthFilter real
  @MockitoBean LoginRateLimiter loginLimiter;
  @MockitoBean RegisterRateLimiter registerLimiter;
  @MockitoBean VerificacaoEmailService verificacao;

  @Test
  void registerValidoRetorna201SemTokenNemUserEComInstrucaoDeVerificar() throws Exception {
    when(authService.register(any()))
        .thenReturn(new AuthDtos.MensagemResponse("Verifique o e-mail."));

    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(
                    "{\"email\":\"a@b.dev\",\"password\":\"12345678\",\"displayName\":\"Ana\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").doesNotExist())
        .andExpect(jsonPath("$.user").doesNotExist()) // nada de dados da conta em duplicata
        .andExpect(jsonPath("$.message").value("Verifique o e-mail."));
  }

  @Test
  void registerComSenhaCurtaRetorna400ENemChamaOService() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content("{\"email\":\"a@b.dev\",\"password\":\"curta\",\"displayName\":\"Ana\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
    verify(authService, never()).register(any());
  }

  @Test
  void loginValidoSemRateLimitRetorna200() throws Exception {
    when(authService.login(any())).thenReturn(new AuthDtos.AuthResponse("jwt-mock", null));

    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"a@b.dev\",\"password\":\"12345678\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("jwt-mock"));
  }

  @Test
  void loginComRateLimitEstouradoRetorna429() throws Exception {
    doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas"))
        .when(loginLimiter)
        .check(any());

    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"a@b.dev\",\"password\":\"12345678\"}"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void loginComCredenciaisInvalidasRetorna401Nao500() throws Exception {
    when(authService.login(any())).thenThrow(new InvalidCredentialsException());

    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"a@b.dev\",\"password\":\"errada1234\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("E-mail ou senha inválidos."));
  }

  @Test
  void loginComEmailVazioNemPassaDoValidationNemDoRateLimit() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"\",\"password\":\"12345678\"}"))
        .andExpect(status().isBadRequest());
    verify(loginLimiter, never()).check(any());
  }
}
