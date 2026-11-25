package br.com.favorito.api.favorites;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.favorito.api.auth.JwtAuthFilter;
import br.com.favorito.api.config.SecurityConfig;
import br.com.favorito.api.config.TestPropertiesConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Camada HTTP dos favoritos: o anyRequest().authenticated() da cadeia REAL (SecurityConfig +
 * JwtAuthFilter importados de verdade) bloqueando sem login e o controller repassando ao service
 * mockado. Login simulado com o post processor user() (o @WithMockUser não atravessa o filtro no
 * Boot 4).
 */
@WebMvcTest(FavoriteController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, TestPropertiesConfig.class})
@TestPropertySource(
    properties = {
      "app.cors.allowed-origins=http://localhost:4200",
      "riot.api.key=test-key",
      "riot.api.default-platform=br1"
    })
class FavoriteControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean FavoriteService favoriteService;
  @MockitoBean br.com.favorito.api.auth.JwtService jwtService; // dependência do filtro real
  @MockitoBean br.com.favorito.api.auth.UserRepository users; // revisão de sessão (token_ver)

  private Favorite favorito(long id, String championId, String name) {
    Favorite f = new Favorite();
    f.setId(id);
    f.setUserId(7L);
    f.setChampionId(championId);
    f.setChampionName(name);
    f.setTitle("A Dama das Sombras");
    f.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    return f;
  }

  @Test
  void listaSemLoginÉBloqueadaPelaCadeiaDeSegurança() throws Exception {
    mvc.perform(get("/api/favorites")).andExpect(status().isForbidden());
    verify(favoriteService, never()).listMine();
  }

  @Test
  void listaComLoginRetorna200EoCorpoDoService() throws Exception {
    when(favoriteService.listMine()).thenReturn(List.of(favorito(1L, "Ahri", "Ahri")));

    mvc.perform(get("/api/favorites").with(user("7")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].championId").value("Ahri"))
        .andExpect(jsonPath("$[0].championName").value("Ahri"));
  }

  @Test
  void favoritaValidoRetorna201() throws Exception {
    when(favoriteService.add(any())).thenReturn(favorito(2L, "Lux", "Lux"));

    mvc.perform(
            post("/api/favorites")
                .with(user("7"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"championId\":\"Lux\",\"championName\":\"Lux\",\"title\":\"A Dama"
                        + " Luminosa\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.championId").value("Lux"))
        .andExpect(jsonPath("$.id").value(2));
  }

  @Test
  void favoritaComChampionIdEmBrancoRetorna400ENemChamaOService() throws Exception {
    mvc.perform(
            post("/api/favorites")
                .with(user("7"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"championId\":\"\",\"championName\":\"Lux\"}"))
        .andExpect(status().isBadRequest());
    verify(favoriteService, never()).add(any());
  }

  @Test
  void removeRetorna200ComRemovedTrue() throws Exception {
    mvc.perform(delete("/api/favorites/Lux").with(user("7")))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"removed\": true}"));
    verify(favoriteService).remove("Lux");
  }

  /** Contrato que o frontend consome (CfFavorite): só campos públicos, nunca o userId interno. */
  @Test
  void listaRetornaContratoDaCfFavoriteSemUserId() throws Exception {
    when(favoriteService.listMine()).thenReturn(List.of(favorito(1L, "Ahri", "Ahri")));

    mvc.perform(get("/api/favorites").with(user("7")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].championId").value("Ahri"))
        .andExpect(jsonPath("$[0].championName").value("Ahri"))
        .andExpect(jsonPath("$[0].title").value("A Dama das Sombras"))
        .andExpect(jsonPath("$[0].createdAt").value("2026-01-01T00:00:00Z"))
        .andExpect(jsonPath("$[0].userId").doesNotExist());
  }

  @Test
  void addRetornaMesmoContratoSemUserId() throws Exception {
    when(favoriteService.add(any())).thenReturn(favorito(2L, "Lux", "Lux"));

    mvc.perform(
            post("/api/favorites")
                .with(user("7"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"championId\":\"Lux\",\"championName\":\"Lux\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(2))
        .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"))
        .andExpect(jsonPath("$.userId").doesNotExist());
  }
}
