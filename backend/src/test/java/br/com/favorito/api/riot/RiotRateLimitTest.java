package br.com.favorito.api.riot;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.favorito.api.auth.JwtAuthFilter;
import br.com.favorito.api.auth.JwtService;
import br.com.favorito.api.auth.UserRepository;
import br.com.favorito.api.common.RateLimiterDeJanela;
import br.com.favorito.api.config.SecurityConfig;
import br.com.favorito.api.config.TestPropertiesConfig;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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

/**
 * Rate limit de /api/riot/**: 10 requisições por minuto por usuário autenticado (a chave é o id
 * vindo do token) MAIS um limite GLOBAL de 40/min compartilhado por todos os usuários; além de
 * qualquer um dos dois → 429. Cadeia REAL (SecurityConfig + JwtAuthFilter); o RiotService é mockado
 * — nada sai para a Riot API.
 */
@WebMvcTest(RiotController.class)
@Import({
  SecurityConfig.class,
  JwtAuthFilter.class,
  TestPropertiesConfig.class,
  RiotRateLimitTest.RelogioControlado.class
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
  @MockitoBean UserRepository users; // revisão de sessão do filtro (token_ver)

  /**
   * Relógio controlável: o limiter real é instanciado com ele, para os testes avançarem o tempo sem
   * dormir. Necessário porque o contador GLOBAL é singleton do contexto: sem janelas novas por
   * método de teste, os métodos contaminariam o bucket uns dos outros (ordem de execução vira
   * acoplamento).
   */
  static final AtomicLong AGORA = new AtomicLong();

  @TestConfiguration
  static class RelogioControlado {

    @Bean
    RateLimiterDeJanela.Relogio relogio() {
      return AGORA::get;
    }

    @Bean
    RiotRateLimiter riotRateLimiter(RateLimiterDeJanela.Relogio relogio) {
      return new RiotRateLimiter(relogio);
    }
  }

  @BeforeEach
  void novaJanelaPorMetodoDeTeste() {
    // cada método avança o relógio um minuto inteiro: janela nova garantida, sem depender de ordem
    AGORA.addAndGet(Duration.ofMinutes(1).toMillis());
  }

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
  void dezBuscasPassam_eADecimaPrimeiraToma429() throws Exception {
    // usuários exclusivos por método: o bean do limiter é singleton do contexto compartilhado
    // entre os métodos desta classe (context caching) — buckets por usuário não podem colidir
    for (int i = 0; i < 10; i++) {
      buscaOk("100");
    }
    // no código anterior (30/min) a 11ª passava com 200 — o limite por usuário é novo
    busca("100", 429);
  }

  @Test
  void outroUsuarioNaoHerdaO429() throws Exception {
    for (int i = 0; i < 11; i++) {
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

  @Test
  void limiteGlobalDe40MinCompartilhadoEntreUsuarios() throws Exception {
    // 4 usuários × 10 requisições cada — cada um dentro do SEU limite por usuário,
    // somando 40 no contador global
    for (int u = 0; u < 4; u++) {
      for (int i = 0; i < 10; i++) {
        buscaOk("u" + u);
      }
    }
    // a 41ª requisição GLOBAL recebe 429 — mesmo sendo a 1ª deste usuário
    busca("u4", 429);
    // e ficou vazada: todo o resto do mundo também está bloqueado na mesma janela
    busca("u5", 429);
  }
}
