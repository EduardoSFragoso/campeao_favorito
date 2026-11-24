package br.com.favorito.api.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.favorito.api.auth.JwtAuthFilter;
import br.com.favorito.api.auth.JwtService;
import br.com.favorito.api.favorites.FavoriteController;
import br.com.favorito.api.favorites.FavoriteService;
import br.com.favorito.api.riot.RiotController;
import br.com.favorito.api.riot.RiotRateLimiter;
import br.com.favorito.api.riot.RiotService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Contrato do {@code anyRequest().authenticated()} da cadeia REAL (SecurityConfig + JwtAuthFilter):
 * toda rota protegida, sem token, tem de cair ANTES do controller com 401 ou 403 — nunca 200 e
 * nunca 500. O status exato (403 hoje, sem entry point configurado) é detalhe de infra; o contrato
 * é "bloqueada e sem chamada de service".
 */
@WebMvcTest({FavoriteController.class, RiotController.class})
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
class RotasProtegidasSemTokenTest {

  @Autowired MockMvc mvc;

  @MockitoBean FavoriteService favoriteService;
  @MockitoBean RiotService riotService;
  @MockitoBean JwtService jwtService; // dependência do JwtAuthFilter real

  private void deveEstarBloqueada(MockHttpServletRequestBuilder request, String rota)
      throws Exception {
    MvcResult result = mvc.perform(request).andReturn();
    int status = result.getResponse().getStatus();
    Assertions.assertTrue(
        status == 401 || status == 403, rota + " sem token deveria ser 401/403, veio " + status);
  }

  @ParameterizedTest(name = "{0} sem token é bloqueada pela cadeia")
  @ValueSource(
      strings = {"GET /api/favorites", "POST /api/favorites", "DELETE /api/favorites/ahri"})
  void rotasDeFavoritosExigemToken(String metodoRota) throws Exception {
    String[] partes = metodoRota.split(" ", 2);
    MockHttpServletRequestBuilder req =
        switch (partes[0]) {
          case "GET" -> MockMvcRequestBuilders.get(partes[1]);
          case "POST" ->
              MockMvcRequestBuilders.post(partes[1])
                  .contentType("application/json")
                  .content("{\"championId\":\"ahri\",\"championName\":\"Ahri\"}");
          default -> MockMvcRequestBuilders.delete(partes[1]);
        };
    deveEstarBloqueada(req, metodoRota);
    verify(favoriteService, never()).listMine();
    verify(favoriteService, never()).add(any());
    verify(favoriteService, never()).remove("ahri");
  }

  @ParameterizedTest(name = "{0} sem token é bloqueada pela cadeia")
  @ValueSource(
      strings = {
        "GET /api/riot/summoner?gameName=Hideonbush&tagLine=KR",
        "GET /api/riot/masteries?gameName=Hideonbush&tagLine=KR",
        "GET /api/riot/rotation"
      })
  void rotasDaRiotExigemToken(String metodoRota) throws Exception {
    deveEstarBloqueada(MockMvcRequestBuilders.get(metodoRota.substring(4)), metodoRota);
    verify(riotService, never()).summonerProfile(any(), any(), any());
    verify(riotService, never()).masteries(any(), any(), any());
    verify(riotService, never()).rotation(any());
  }
}
