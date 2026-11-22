package br.com.favorito.api.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.favorito.api.common.RiotApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do agregador de invocador: degradação do League-V4 (403/404 → perfil sem elo),
 * filtro de entradas sem partidas e cache por chave. O {@link RiotClient} é mockado — nada toca na
 * Riot API de verdade.
 */
class RiotServiceTest {

  private RiotClient client;
  private RiotService service;

  @BeforeEach
  void setUp() {
    client = mock(RiotClient.class);
    service = new RiotService(client);
  }

  private RiotDtos.RiotAccount account() {
    return new RiotDtos.RiotAccount("puuid-1", "Hideonbush", "KR");
  }

  private RiotDtos.RiotSummoner summoner(String summonerId) {
    return new RiotDtos.RiotSummoner(summonerId, "puuid-1", "Hideonbush", "acid-1", 42, 300, 0L);
  }

  private void stubAccountAndSummoner(String summonerId) {
    when(client.getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            eq("hideonbush"),
            eq("KR")))
        .thenReturn(account());
    when(client.getServer(
            eq("br1"),
            contains("/lol/summoner/v4/summoners/by-puuid"),
            eq(RiotDtos.RiotSummoner.class),
            eq("puuid-1")))
        .thenReturn(summoner(summonerId));
  }

  private void stubMasteries() {
    when(client.getServer(
            eq("br1"),
            contains("/champion-masteries/by-puuid"),
            anyMap(),
            eq(RiotDtos.MasteryDto[].class),
            eq("puuid-1")))
        .thenReturn(
            new RiotDtos.MasteryDto[] {
              new RiotDtos.MasteryDto(157, 7, 123456, null, 1700000000000L),
              new RiotDtos.MasteryDto(412, 6, 98765, null, null),
            });
  }

  @Test
  @DisplayName("perfil completo: elo filtrado (entries sem partidas saem) + maestria mapeada")
  void perfilCompleto() {
    stubAccountAndSummoner("sum-id");
    stubMasteries();
    when(client.getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-summoner"),
            eq(RiotDtos.LeagueEntry[].class),
            eq("sum-id")))
        .thenReturn(
            new RiotDtos.LeagueEntry[] {
              new RiotDtos.LeagueEntry(
                  "RANKED_SOLO_5x5", "EMERALD", "II", 75, 120, 100, false, false, false),
              new RiotDtos.LeagueEntry("RANKED_FLEX_SR", "SILVER", "I", 0, 0, 0, false, true, false)
            });

    RiotDtos.SummonerProfile profile = service.summonerProfile("hideonbush", "KR", "br1");

    assertEquals("puuid-1", profile.puuid());
    assertEquals("Hideonbush", profile.gameName());
    assertEquals(300, profile.summonerLevel());
    assertEquals(1, profile.ranked().size()); // entry com 0 partidas é filtrada
    assertEquals("EMERALD", profile.ranked().get(0).tier());
    assertEquals(2, profile.topMasteries().size());
  }

  @Test
  @DisplayName("League-V4 com 403 (chave dev/personal): degrada — perfil sem elo, maestria intacta")
  void degradeEloCom403() {
    stubAccountAndSummoner("sum-403");
    stubMasteries();
    when(client.getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-summoner"),
            eq(RiotDtos.LeagueEntry[].class),
            eq("sum-403")))
        .thenThrow(new RiotApiException(403, "chave inválida"));

    RiotDtos.SummonerProfile profile = service.summonerProfile("hideonbush", "KR", "br1");

    assertEquals(0, profile.ranked().size());
    assertEquals(2, profile.topMasteries().size());
  }

  @Test
  @DisplayName("League-V4 com 404 (invocador sem ranqueada): degrada — perfil sem elo")
  void degradeEloCom404() {
    stubAccountAndSummoner("sum-404");
    stubMasteries();
    when(client.getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-summoner"),
            eq(RiotDtos.LeagueEntry[].class),
            eq("sum-404")))
        .thenThrow(new RiotApiException(404, "sem entradas"));

    RiotDtos.SummonerProfile profile = service.summonerProfile("hideonbush", "KR", "br1");

    assertEquals(0, profile.ranked().size());
  }

  @Test
  @DisplayName("League-V4 com outro status (ex.: 429): propaga o erro — não degrada em silêncio")
  void outrosStatusPropagam() {
    stubAccountAndSummoner("sum-429");
    stubMasteries();
    when(client.getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-summoner"),
            eq(RiotDtos.LeagueEntry[].class),
            eq("sum-429")))
        .thenThrow(new RiotApiException(429, "rate limit"));

    RiotApiException ex =
        assertThrows(
            RiotApiException.class, () -> service.summonerProfile("hideonbush", "KR", "br1"));
    assertEquals(429, ex.upstreamStatus);
  }

  @Test
  @DisplayName("conta inexistente: RiotApiException 404")
  void contaInexistente() {
    when(client.getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            eq("naoexiste"),
            eq("BR1")))
        .thenReturn(null);

    RiotApiException ex =
        assertThrows(
            RiotApiException.class, () -> service.summonerProfile("naoexiste", "BR1", "br1"));
    assertEquals(404, ex.upstreamStatus);
  }

  @Test
  @DisplayName("conta inexistente (client lança 404): mensagem manda conferir nome/tag/região")
  void contaInexistenteComExcecaoDoClient() {
    when(client.getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            eq("hideonbush"),
            eq("KR")))
        .thenThrow(new RiotApiException(404, "Não encontrado na Riot API."));

    RiotApiException ex =
        assertThrows(
            RiotApiException.class, () -> service.summonerProfile("hideonbush", "KR", "br1"));
    assertEquals(404, ex.upstreamStatus);
    assertEquals(
        "Conta \"HIDEONBUSH#KR\" não encontrada — confira nome, tag e região (?platform=).",
        ex.getMessage());
  }

  @Test
  @DisplayName("conta existe mas sem LoL no servidor (summoner 404): mensagem diferenciada")
  void contaExisteMasSemLolNoServidor() {
    when(client.getRegional(
            eq("kr"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            eq("hideonbush"),
            eq("KR")))
        .thenReturn(account());
    when(client.getServer(
            eq("kr"),
            contains("/lol/summoner/v4/summoners/by-puuid"),
            eq(RiotDtos.RiotSummoner.class),
            eq("puuid-1")))
        .thenThrow(new RiotApiException(404, "Não encontrado na Riot API."));

    RiotApiException ex =
        assertThrows(
            RiotApiException.class, () -> service.summonerProfile("hideonbush", "KR", "kr"));
    assertEquals(404, ex.upstreamStatus);
    assertEquals(
        "Conta \"HIDEONBUSH#KR\" existe, mas não tem LoL no servidor \"kr\". Confira a região"
            + " (?platform=).",
        ex.getMessage());
  }

  @Test
  @DisplayName("summoner com outro status (ex.: 500): propaga — não vira mensagem de sem-LoL")
  void summonerComOutroStatusPropaga() {
    when(client.getRegional(
            eq("kr"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            eq("hideonbush"),
            eq("KR")))
        .thenReturn(account());
    when(client.getServer(
            eq("kr"),
            contains("/lol/summoner/v4/summoners/by-puuid"),
            eq(RiotDtos.RiotSummoner.class),
            eq("puuid-1")))
        .thenThrow(new RiotApiException(500, "Riot API retornou HTTP 500."));

    RiotApiException ex =
        assertThrows(
            RiotApiException.class, () -> service.summonerProfile("hideonbush", "KR", "kr"));
    assertEquals(500, ex.upstreamStatus);
  }

  @Test
  @DisplayName("cache: segunda busca do mesmo invocador não refaz as chamadas à Riot")
  void cacheEvitaRebusca() {
    stubAccountAndSummoner("sum-cache");
    stubMasteries();
    when(client.getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-summoner"),
            eq(RiotDtos.LeagueEntry[].class),
            eq("sum-cache")))
        .thenReturn(new RiotDtos.LeagueEntry[0]);

    service.summonerProfile("hideonbush", "KR", "br1");
    service.summonerProfile("Hideonbush", "kr", "br1");

    verify(client, times(1))
        .getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            any(),
            any());
    verify(client, times(1))
        .getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-summoner"),
            eq(RiotDtos.LeagueEntry[].class),
            any()); // ranked é cacheado por summoner id — a 2ª busca vem do cache
  }
}
