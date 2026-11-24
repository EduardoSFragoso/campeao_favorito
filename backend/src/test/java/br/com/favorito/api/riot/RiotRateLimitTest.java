package br.com.favorito.api.riot;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.favorito.api.auth.JwtAuthFilter;
import br.com.favorito.api.auth.JwtService;
import br.com.favorito.api.config.SecurityConfig;
import br.com.favorito.api.config.TestPropertiesConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Rate limit de /api/riot/**: 30 requisições por minuto por usuário autenticado (a chave é o id
 * vindo do token), 31ª → 429. Cadeia REAL (SecurityConfig + JwtAuthFilter); o RiotService é mockado
 * — nada sai para a Riot API.
 */
@WebMvcTest(RiotController.class)
@Import({
  SecurityConfig.class,
  JwtAuthFilter.class,
  TestPropertiesConfig.class,
  RiotRateLimiter.class
})
@TestPropertySource(
    properties = {
      "app.cors.allowed-origins=http://localhost:4200",
      "riot.api.key=test-key",
      "riot.api.default-platform=br1"
    })
class RiotRateLimitTest {

  @Autowired MockMvc mvc;

  @MockitoBean RiotService riotService;
  @MockitoBean JwtService jwtService; // dependência do JwtAuthFilter real

  @BeforeEach
  void preparaMock() {
    when(riotService.rotation(anyString()))
        .thenReturn(new RiotDtos.ChampionRotation(10, List.of(266, 103), List.of(84)));
  }

  private void busca(String userId, int statusEsperado) throws Exception {
    mvc.perform(get("/api/riot/rotation").with(user(userId)))
        .andExpect(status().is(statusEsperado))
        .andExpect(jsonPath("$.error").exists());
  }

  private void buscaOk(String userId) throws Exception {
    mvc.perform(get("/api/riot/rotation").with(user(userId))).andExpect(status().isOk());
  }

  @Test
  void trintaBuscasPassam_eATrigesimaPrimeiraToma429() throws Exception {
    // usuários exclusivos por método: o bean do limiter é singleton do contexto compartilhado
    // entre os métodos desta classe (context caching) — buckets por usuário não podem colidir
    for (int i = 0; i < 30; i++) {
      buscaOk("100");
    }
    // no código atual a 31ª passava (200) — o limite é novo
    busca("100", 429);
  }

  @Test
  void outroUsuarioNaoHerdaO429() throws Exception {
    for (int i = 0; i < 31; i++) {
      try {
        buscaOk("200");
      } catch (AssertionError ignored) {
        // apenas esgota a janela do primeiro usuário
      }
    }
    buscaOk("300");
    // e o usuário 200 segue bloqueado na MESMA janela (chave por usuário, não global)
    busca("200", 429);
  }
}
