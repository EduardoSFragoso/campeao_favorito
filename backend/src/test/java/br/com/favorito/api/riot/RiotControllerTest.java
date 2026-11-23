package br.com.favorito.api.riot;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.favorito.api.auth.JwtAuthFilter;
import br.com.favorito.api.config.SecurityConfig;
import br.com.favorito.api.config.TestPropertiesConfig;
import br.com.favorito.api.riot.RiotDtos.ChampionRotation;
import br.com.favorito.api.riot.RiotDtos.SummonerProfile;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Camada HTTP do Riot: validação de gameName/tagLine caindo no GlobalExceptionHandler (400) e a
 * platform default vinda do RiotProperties de verdade (via TestPropertiesConfig), não mockada.
 */
@WebMvcTest(RiotController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, TestPropertiesConfig.class})
@TestPropertySource(
    properties = {
      "app.cors.allowed-origins=http://localhost:4200",
      "riot.api.key=test-key",
      "riot.api.default-platform=br1"
    })
class RiotControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean RiotService riotService;
  @MockitoBean br.com.favorito.api.auth.JwtService jwtService; // dependência do filtro real

  private SummonerProfile perfil() {
    return new SummonerProfile(
        "puuid-mock", "Galuverior", "BR1", null, 536, 298, List.of(), List.of());
  }

  @Test
  void summonerSemLoginÉBloqueadoPelaCadeiaDeSegurança() throws Exception {
    mvc.perform(get("/api/riot/summoner").param("gameName", "Galuverior").param("tagLine", "BR1"))
        .andExpect(status().isForbidden());
    verify(riotService, never()).summonerProfile("Galuverior", "BR1", "br1");
  }

  @Test
  void summonerComLoginUsaAPlatformDefaultDasPropriedades() throws Exception {
    when(riotService.summonerProfile("Galuverior", "BR1", "br1")).thenReturn(perfil());

    mvc.perform(
            get("/api/riot/summoner")
                .with(user("7"))
                .param("gameName", "Galuverior")
                .param("tagLine", "BR1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gameName").value("Galuverior"))
        .andExpect(jsonPath("$.tagLine").value("BR1"))
        .andExpect(jsonPath("$.summonerLevel").value(298));
  }

  @Test
  void summonerComPlatformExplicitaRepassaParaOService() throws Exception {
    when(riotService.summonerProfile("Hide on bush", "KR1", "kr")).thenReturn(perfil());

    mvc.perform(
            get("/api/riot/summoner")
                .with(user("7"))
                .param("gameName", "Hide on bush")
                .param("tagLine", "KR1")
                .param("platform", "kr"))
        .andExpect(status().isOk());
  }

  @Test
  void gameNameComBarraÉRejeitadoCom400() throws Exception {
    // barra não entra no pattern unicode-safe: é o mesmo caso do SSRF testado antes
    mvc.perform(
            get("/api/riot/summoner")
                .with(user("7"))
                .param("gameName", "evil.com/x")
                .param("tagLine", "BR1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
    verify(riotService, never()).summonerProfile("evil.com/x", "BR1", "br1");
  }

  @Test
  void tagLineComUmCaractereÉRejeitadaCom400() throws Exception {
    mvc.perform(
            get("/api/riot/summoner")
                .with(user("7"))
                .param("gameName", "Galuverior")
                .param("tagLine", "K"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void rotationComLoginRetornaOCorpoDoService() throws Exception {
    when(riotService.rotation("br1"))
        .thenReturn(new ChampionRotation(10, List.of(266, 103), List.of(84)));

    mvc.perform(get("/api/riot/rotation").with(user("7")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.freeChampionIds[0]").value(266));
  }

  // ---------- masteries ----------

  private List<RiotDtos.MasteryDto> maestrias() {
    return List.of(new RiotDtos.MasteryDto(157, 7, 123456, null, 1700000000000L));
  }

  @Test
  void masteriesSemLoginÉBloqueadoPelaCadeiaDeSegurança() throws Exception {
    mvc.perform(get("/api/riot/masteries").param("gameName", "Galuverior").param("tagLine", "BR1"))
        .andExpect(status().isForbidden());
    verify(riotService, never()).masteries("Galuverior", "BR1", "br1");
  }

  @Test
  void masteriesComLoginDelegaComAPlatformDefault() throws Exception {
    when(riotService.masteries("Galuverior", "BR1", "br1")).thenReturn(maestrias());

    mvc.perform(
            get("/api/riot/masteries")
                .with(user("7"))
                .param("gameName", "Galuverior")
                .param("tagLine", "BR1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].championId").value(157))
        .andExpect(jsonPath("$[0].championLevel").value(7));
  }

  @Test
  void masteriesComGameNameComBarraÉRejeitadoCom400() throws Exception {
    mvc.perform(
            get("/api/riot/masteries")
                .with(user("7"))
                .param("gameName", "evil.com/x")
                .param("tagLine", "BR1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
    verify(riotService, never()).masteries("evil.com/x", "BR1", "br1");
  }
}
