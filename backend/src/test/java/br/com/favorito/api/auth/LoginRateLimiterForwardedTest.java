package br.com.favorito.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.favorito.api.config.SecurityConfig;
import br.com.favorito.api.config.TestPropertiesConfig;
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
 * Rate limit do login atrás do nginx: em produção o proxy repassa o IP real no X-Forwarded-For e
 * {@code server.forward-headers-strategy: framework} (application-prod.yml) faz o getRemoteAddr()
 * do controller ver o IP do cliente. Aqui a cadeia (SecurityConfig + JwtAuthFilter) e o
 * LoginRateLimiter são os beans REAIS. Detalhe de infra testada: o slice @WebMvcTest NÃO
 * autoconfigura o filtro de forwarded headers da strategy (em servidor real quem o registra é o
 * WebMvcAutoConfiguration), então o filtro REAL do spring-web (ForwardedHeaderFilter) é registrado
 * aqui explicitamente — o mesmo comportamento do prod.
 */
@WebMvcTest(AuthController.class)
@Import({
  SecurityConfig.class,
  JwtAuthFilter.class,
  TestPropertiesConfig.class,
  LoginRateLimiter.class,
  RegisterRateLimiter.class,
  LoginRateLimiterForwardedTest.FiltroDeProducao.class
})
@TestPropertySource(
    properties = {
      "app.cors.allowed-origins=http://localhost:4200",
      "riot.api.key=test-key",
      "riot.api.default-platform=br1",
      "server.forward-headers-strategy=framework"
    })
class LoginRateLimiterForwardedTest {

  /** Espelho do que a strategy framework registra no servidor de produção. */
  @TestConfiguration
  static class FiltroDeProducao {
    @Bean
    ForwardedHeaderFilter forwardedHeaderFilter() {
      return new ForwardedHeaderFilter();
    }
  }

  private static final String LOGIN = "{\"email\":\"a@b.dev\",\"password\":\"12345678\"}";

  @Autowired MockMvc mvc;

  @MockitoBean AuthService authService;
  @MockitoBean JwtService jwtService; // dependência do JwtAuthFilter real
  @MockitoBean UserRepository users; // revisão de sessão do filtro (token_ver)
  @MockitoBean CurrentUserService currentUser; // dependência do AuthController (logout)
  @MockitoBean VerificacaoEmailService verificacao;

  private void loginCom(String forwardedFor) throws Exception {
    var request = post("/api/auth/login").contentType("application/json").content(LOGIN);
    if (forwardedFor != null) {
      request = request.header("X-Forwarded-For", forwardedFor);
    }
    mvc.perform(request).andExpect(status().isOk());
  }

  private void loginComEsperando(String forwardedFor, int status) throws Exception {
    var request = post("/api/auth/login").contentType("application/json").content(LOGIN);
    if (forwardedFor != null) {
      request = request.header("X-Forwarded-For", forwardedFor);
    }
    mvc.perform(request).andExpect(status().is(status)).andExpect(jsonPath("$.error").exists());
  }

  @Test
  void onzeLoginsDoMesmoXForwardedForViram429() throws Exception {
    when(authService.login(any()))
        .thenReturn(new AuthDtos.AuthResponse("jwt-mock", "refresh-mock", null));

    for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
      loginCom("9.8.7.6");
    }
    loginComEsperando("9.8.7.6", 429);
  }

  @Test
  void outroIpNoXForwardedForNaoHerdaOBloqueio() throws Exception {
    when(authService.login(any()))
        .thenReturn(new AuthDtos.AuthResponse("jwt-mock", "refresh-mock", null));

    for (int i = 0; i <= LoginRateLimiter.MAX_ATTEMPTS; i++) {
      try {
        loginCom("1.1.1.1");
      } catch (AssertionError ignored) {
        // apenas esgota a janela do primeiro IP
      }
    }
    loginCom("5.5.5.5");
  }

  @Test
  void loginsAtrasDoNginxSemHeaderFormamBucketProprio() throws Exception {
    when(authService.login(any()))
        .thenReturn(new AuthDtos.AuthResponse("jwt-mock", "refresh-mock", null));

    // tentativas sem XFF (bucket do loopback 127.0.0.1) não punem um IP real encaminhado
    for (int i = 0; i <= LoginRateLimiter.MAX_ATTEMPTS; i++) {
      try {
        loginCom(null);
      } catch (AssertionError ignored) {
        // apenas esgota a janela do bucket loopback
      }
    }
    loginCom("9.9.9.9");
  }

  @Test
  void primeiroIpDoXForwardedForEUsoComoBucket() throws Exception {
    when(authService.login(any()))
        .thenReturn(new AuthDtos.AuthResponse("jwt-mock", "refresh-mock", null));

    // cadeia "cliente, proxy1, proxy2": a strategy usa o primeiro à esquerda
    for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
      loginCom("7.7.7.7, 10.0.0.1, 10.0.0.2");
    }
    loginComEsperando("7.7.7.7, 10.0.0.1, 10.0.0.2", 429);
  }
}
