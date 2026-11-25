package br.com.favorito.api.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O League-V4 le pela chave {@code /lol/league/v4/entries/by-summoner/{summonerId}}, que a Riot
 * REMOVEU (anúncio de jun/2025: a leitura passa a ser por PUUID, {@code entries/by-puuid}). Estes
 * testes falham no código antigo (que ainda chama by-summoner) e fixam também a chave de cache do
 * ranking por PUUID — {@code summoner.id()} pode vir null e não pode participar dela.
 */
class RiotServiceLeaguePuuidTest {

  private RiotClient client;
  private RiotService service;

  @BeforeEach
  void setUp() {
    client = org.mockito.Mockito.mock(RiotClient.class);
    service = new RiotService(client);
  }

  private void stubFelicidadeBasica() {
    when(client.getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            eq("hideonbush"),
            eq("KR")))
        .thenReturn(new RiotDtos.RiotAccount("puuid-1", "Hideonbush", "KR"));
    when(client.getServer(
            eq("br1"),
            contains("/lol/summoner/v4/summoners/by-puuid"),
            eq(RiotDtos.RiotSummoner.class),
            eq("puuid-1")))
        .thenReturn(
            new RiotDtos.RiotSummoner("sum-id", "puuid-1", "Hideonbush", "acid-1", 42, 300, 0L));
    when(client.getServer(
            eq("br1"),
            contains("/champion-masteries/by-puuid"),
            org.mockito.ArgumentMatchers.anyMap(),
            eq(RiotDtos.MasteryDto[].class),
            eq("puuid-1")))
        .thenReturn(new RiotDtos.MasteryDto[] {});
  }

  @Test
  @DisplayName("elo é lido de league/v4/entries/by-puuid (by-summoner foi removida pela Riot)")
  void eloLidoDaByPuuid() {
    stubFelicidadeBasica();
    when(client.getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-puuid"),
            eq(RiotDtos.LeagueEntry[].class),
            eq("puuid-1")))
        .thenReturn(
            new RiotDtos.LeagueEntry[] {
              new RiotDtos.LeagueEntry(
                  "RANKED_SOLO_5x5", "EMERALD", "II", 75, 120, 100, false, false, false)
            });

    RiotDtos.SummonerProfile profile = service.summonerProfile("hideonbush", "KR", "br1");

    assertEquals(1, profile.ranked().size(), "elo precisa vir do endpoint by-puuid");
    assertEquals("EMERALD", profile.ranked().get(0).tier());
    verify(client)
        .getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-puuid"),
            eq(RiotDtos.LeagueEntry[].class),
            eq("puuid-1"));
  }

  @Test
  @DisplayName(
      "cache do ranking é chaveado pelo PUUID e summoner.id() null não quebra nem entra na chave")
  void cacheDoRankingUsaPuuidEToleraSummonerIdNulo() {
    // summoner.id() chega null (a Riot pode omitir); o ranking ainda precisa ser lido e
    // taxado por PUUID
    when(client.getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            eq("hideonbush"),
            eq("KR")))
        .thenReturn(new RiotDtos.RiotAccount("puuid-1", "Hideonbush", "KR"));
    when(client.getServer(
            eq("br1"),
            contains("/lol/summoner/v4/summoners/by-puuid"),
            eq(RiotDtos.RiotSummoner.class),
            eq("puuid-1")))
        .thenReturn(
            new RiotDtos.RiotSummoner(null, "puuid-1", "Hideonbush", "acid-1", 42, 300, 0L));
    when(client.getServer(
            eq("br1"),
            contains("/champion-masteries/by-puuid"),
            org.mockito.ArgumentMatchers.anyMap(),
            eq(RiotDtos.MasteryDto[].class),
            eq("puuid-1")))
        .thenReturn(new RiotDtos.MasteryDto[] {});
    when(client.getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-puuid"),
            eq(RiotDtos.LeagueEntry[].class),
            eq("puuid-1")))
        .thenReturn(
            new RiotDtos.LeagueEntry[] {
              new RiotDtos.LeagueEntry(
                  "RANKED_SOLO_5x5", "EMERALD", "II", 75, 120, 100, false, false, false)
            });

    RiotDtos.SummonerProfile primeira = service.summonerProfile("hideonbush", "KR", "br1");
    RiotDtos.SummonerProfile segunda = service.summonerProfile("hideonbush", "KR", "br1");

    assertEquals(1, primeira.ranked().size());
    assertEquals(1, segunda.ranked().size());
    // chama a Riot UMA vez: com summoner.id() null a chave atual vira "ranked:null" ou NPE —
    // pelo PUUID, a segunda leitura sai do cache
    verify(client, times(1))
        .getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-puuid"),
            eq(RiotDtos.LeagueEntry[].class),
            eq("puuid-1"));
  }
}
