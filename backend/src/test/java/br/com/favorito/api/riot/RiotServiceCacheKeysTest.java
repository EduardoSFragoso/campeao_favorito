package br.com.favorito.api.riot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.favorito.api.riot.RiotDtos.RiotAccount;
import br.com.favorito.api.riot.RiotDtos.RiotSummoner;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Semântica das chaves de cache do RiotService (rede de segurança contra colisão): o perfil cacheia
 * por "profile:{platform}:{GAMENAME#TAG}" (case-insensitive de propósito — a Riot trata
 * gameName#tagLine sem case), o rank e a maestria por puuid. Contas diferentes NÃO podem dividir
 * entrada e a mesma conta em plataforma diferente NÃO pode reusar cache.
 */
class RiotServiceCacheKeysTest {

  private RiotClient client;
  private RiotService service;

  @BeforeEach
  void setUp() {
    client = mock(RiotClient.class);
    service = new RiotService(client);
  }

  private void stubConta(
      String gameName, String tagLine, String platform, String puuid, String summonerId) {
    when(client.getRegional(
            eq(platform),
            contains("/accounts/by-riot-id"),
            eq(RiotAccount.class),
            eq(gameName),
            eq(tagLine)))
        .thenReturn(new RiotAccount(puuid, gameName, tagLine));
    when(client.getServer(
            eq(platform),
            contains("/lol/summoner/v4/summoners/by-puuid"),
            eq(RiotSummoner.class),
            eq(puuid)))
        .thenReturn(new RiotSummoner(summonerId, puuid, gameName, "acid-1", 42, 300, 0L));
    stubRanked(platform, puuid);
    stubMaestrias(platform, puuid);
  }

  private void stubRanked(String platform, String puuid) {
    when(client.getServer(
            eq(platform),
            contains("/lol/league/v4/entries/by-puuid"),
            eq(RiotDtos.LeagueEntry[].class),
            eq(puuid)))
        .thenReturn(new RiotDtos.LeagueEntry[0]);
  }

  private void stubMaestrias(String platform, String puuid) {
    Map<String, Object> count5 = new HashMap<>();
    count5.put("count", 5);
    when(client.getServer(
            eq(platform),
            contains("/champion-masteries/by-puuid"),
            eq(count5),
            eq(RiotDtos.MasteryDto[].class),
            eq(puuid)))
        .thenReturn(new RiotDtos.MasteryDto[0]);
  }

  @Test
  @DisplayName("cache: mesma conta em plataformas diferentes são entradas distintas")
  void mesmaContaPlataformasDistintasNaoCompartilhaCache() {
    stubConta("Hideonbush", "KR", "br1", "puuid-br", "sum-br");
    stubConta("Hideonbush", "KR", "na1", "puuid-na", "sum-na");

    service.summonerProfile("Hideonbush", "KR", "br1");
    service.summonerProfile("Hideonbush", "KR", "na1");

    // se a chave não incluísse a platform, a 2ª busca viria do cache da 1ª (0 chamadas em na1)
    verify(client, times(1))
        .getRegional(
            eq("br1"), contains("/accounts/by-riot-id"), eq(RiotAccount.class), any(), any());
    verify(client, times(1))
        .getRegional(
            eq("na1"), contains("/accounts/by-riot-id"), eq(RiotAccount.class), any(), any());
  }

  @Test
  @DisplayName("cache: contas diferentes não dividem ranked nem maestria (ambos por puuid)")
  void contasDistintasNaoDividemSubcaches() {
    stubConta("Hideonbush", "KR", "br1", "puuid-1", "sum-1");
    stubConta("Faker", "KR", "br1", "puuid-2", "sum-2");

    service.summonerProfile("Hideonbush", "KR", "br1");
    service.summonerProfile("Faker", "KR", "br1");

    // ranked é cacheado por puuid: cada conta teve a SUA busca (times(1) com o puuid próprio)
    verify(client, times(1))
        .getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-puuid"),
            eq(RiotDtos.LeagueEntry[].class),
            eq("puuid-1"));
    verify(client, times(1))
        .getServer(
            eq("br1"),
            contains("/lol/league/v4/entries/by-puuid"),
            eq(RiotDtos.LeagueEntry[].class),
            eq("puuid-2"));
    // idem maestria por puuid
    verify(client, times(1))
        .getServer(
            eq("br1"),
            contains("/champion-masteries/by-puuid"),
            eq(Map.of("count", 5)),
            eq(RiotDtos.MasteryDto[].class),
            eq("puuid-1"));
    verify(client, times(1))
        .getServer(
            eq("br1"),
            contains("/champion-masteries/by-puuid"),
            eq(Map.of("count", 5)),
            eq(RiotDtos.MasteryDto[].class),
            eq("puuid-2"));
  }
}
