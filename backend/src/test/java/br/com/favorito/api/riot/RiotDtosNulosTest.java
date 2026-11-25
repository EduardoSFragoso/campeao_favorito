package br.com.favorito.api.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tolerância da leitura dos DTOs da Riot a "null" em campos numéricos: a Riot API já mandou null
 * (ex.: "chestGranted" removido) e campos novos podem voltar null. O cliente lê as respostas com um
 * mapper Jackson com as configurações padrão (mesmo mapper dos conversores do RestClient padrão),
 * então null em int/long não pode estourar exceção — vira o valor padrão 0.
 */
class RiotDtosNulosTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  private <T> T ler(String json, Class<T> tipo) {
    return mapper.readValue(json, tipo);
  }

  @Test
  @DisplayName("ChampionRotation com maxNewPlayerLevel null → 0 e listas null → vazias")
  void rotacaoComNumerosNulos() {
    var rotacao =
        ler(
            """
            {"maxNewPlayerLevel": null,
             "freeChampionIds": null,
             "freeChampionIdsForNewPlayers": [7, 19]}
            """,
            RiotDtos.ChampionRotation.class);

    assertEquals(0, rotacao.maxNewPlayerLevel(), "null em campo numérico vira padrão 0");
    assertTrue(rotacao.freeChampionIds().isEmpty(), "lista null vira vazia, nunca null");
    assertEquals(List.of(7, 19), rotacao.freeChampionIdsForNewPlayers());
  }

  @Test
  @DisplayName("RiotSummoner com profileIconId/summonerLevel/revisionDate null → 0")
  void summonerComNumerosNulos() {
    var summoner =
        ler(
            """
            {"id": "s1", "puuid": "p1", "name": "Joao", "accountId": "a1",
             "profileIconId": null, "summonerLevel": null, "revisionDate": null}
            """,
            RiotDtos.RiotSummoner.class);

    assertEquals(0, summoner.profileIconId());
    assertEquals(0L, summoner.summonerLevel());
    assertEquals(0L, summoner.revisionDate());
    assertEquals("s1", summoner.id());
    assertEquals("Joao", summoner.name());
  }

  @Test
  @DisplayName("LeagueEntry com leaguePoints/wins/losses null → 0 e helpers não estouram")
  void leagueEntryComNumerosNulos() {
    var entrada =
        ler(
            """
            {"queueType": "RANKED_SOLO_5x5", "tier": "SILVER", "rank": "II",
             "leaguePoints": null, "wins": null, "losses": null,
             "veteran": false, "inactive": false, "freshBlood": false}
            """,
            RiotDtos.LeagueEntry.class);

    assertEquals(0, entrada.leaguePoints());
    assertEquals(0, entrada.wins());
    assertEquals(0, entrada.losses());
    assertEquals(0, entrada.games());
    assertEquals(0.0, entrada.winratePercent());
  }

  @Test
  @DisplayName("MasteryDto com championId/championLevel/championPoints null → 0")
  void masteryComNumerosNulos() {
    var mastery =
        ler(
            """
            {"championId": null, "championLevel": null, "championPoints": null,
             "chestGranted": null, "lastPlayTime": null}
            """,
            RiotDtos.MasteryDto.class);

    assertEquals(0, mastery.championId());
    assertEquals(0, mastery.championLevel());
    assertEquals(0, mastery.championPoints());
  }

  @Test
  @DisplayName("SummonerProfile com profileIconId/summonerLevel null → 0")
  void perfilComNumerosNulos() {
    var perfil =
        ler(
            """
            {"puuid": "p1", "gameName": "Joao", "tagLine": "BR1", "name": "Joao",
             "profileIconId": null, "summonerLevel": null,
             "ranked": [], "topMasteries": []}
            """,
            RiotDtos.SummonerProfile.class);

    assertEquals(0, perfil.profileIconId());
    assertEquals(0L, perfil.summonerLevel());
    assertEquals("p1", perfil.puuid());
  }
}
