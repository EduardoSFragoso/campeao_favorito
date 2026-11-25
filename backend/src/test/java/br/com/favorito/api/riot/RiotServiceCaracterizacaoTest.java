package br.com.favorito.api.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.favorito.api.common.RiotApiException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Caracterização do {@link RiotService} — descreve o comportamento atual (inclusive o duplicado,
 * que é a alvo da pendência de refatoração) e FALHA se ele mudar. O refactor pode alterar estes
 * pinos: aí se atualiza o teste conscientemente, com a mudança discutida no PR.
 *
 * <p>Pinos: (1) a resolução de conta é REPETIDA entre summonerProfile e masteries (cada endpoint
 * resolve a própria conta); (2) gameName/tagLine diferente só em caixa COMPARTILHAM o cache (a
 * chave é em caixa alta) mas o client recebe o valor com a caixa original e trimado; (3) a mensagem
 * 404 de conta não encontrada menciona a chave em caixa alta; (4) degradação do elo (403/404) é
 * armazenada no cache junto com o perfil inteiro — não se tenta de novo dentro do TTL; (5) rotação
 * null → 404 "Rotação indisponível."; (6) erro não-404 na conta propaga e não deixa carga no cache.
 */
class RiotServiceCaracterizacaoTest {

  private RiotClient client;
  private RiotService service;

  @BeforeEach
  void setUp() {
    client = mock(RiotClient.class);
    service = new RiotService(client);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapaInterno(String campo) throws Exception {
    Field f = RiotService.class.getDeclaredField(campo);
    f.setAccessible(true);
    return (Map<String, Object>) f.get(service);
  }

  private void stubFelicidade() {
    when(client.getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            any(),
            any()))
        .thenReturn(new RiotDtos.RiotAccount("puuid-1", "catan", "TST"));

    when(client.getServer(
            eq("br1"),
            contains("/lol/summoner/v4/summoners/by-puuid"),
            eq(RiotDtos.RiotSummoner.class),
            eq("puuid-1")))
        .thenReturn(new RiotDtos.RiotSummoner("sid-1", "puuid-1", "catan", "acid-1", 42, 300, 0L));

    when(client.getServer(
            eq("br1"), contains("/league/v4/entries"), eq(RiotDtos.LeagueEntry[].class), any()))
        .thenReturn(new RiotDtos.LeagueEntry[0]);

    // /top (perfil) — com query, usa anyMap()
    when(client.getServer(
            eq("br1"),
            contains("/champion-masteries/by-puuid"),
            anyMap(),
            eq(RiotDtos.MasteryDto[].class),
            any()))
        .thenReturn(new RiotDtos.MasteryDto[0]);
    // sem /top (masteries) — sem query
    when(client.getServer(
            eq("br1"),
            contains("/champion-masteries/by-puuid"),
            eq(RiotDtos.MasteryDto[].class),
            any()))
        .thenReturn(new RiotDtos.MasteryDto[0]);
  }

  @Test
  @DisplayName(
      "conta em caixa diferente compartilha a MESMA entrada de cache (chave em caixa alta)")
  void caixaDiferenteCompartilhaCache() {
    stubFelicidade();

    service.summonerProfile("hideonbush", "KR", "br1");
    service.summonerProfile("HIDEONBUSH", "kr", "br1");

    // a segunda chamada é cache hit: client foi à conta UMA vez
    verify(client, times(1))
        .getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            any(),
            any());
  }

  @Test
  @DisplayName("gameName vai para o client trimado e com a caixa original (árroba nada na chave)")
  void gameNameTrimadoParaOClient() {
    stubFelicidade();

    service.summonerProfile("  Hide on bush  ", "KR", "br1");

    // o client recebe o valor trimado, caixa original preservada (só a CHAVE é maiúscula)
    verify(client)
        .getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            eq("Hide on bush"),
            eq("KR"));
  }

  @Test
  @DisplayName("account com puuid null: 404 com a chave em caixa alta na mensagem")
  void puuidNullVira404ComChaveMaiuscula() {
    when(client.getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            eq("hideonbush"),
            eq("KR")))
        .thenReturn(new RiotDtos.RiotAccount(null, "hideonbush", "KR"));

    RiotApiException e =
        assertThrows(
            RiotApiException.class, () -> service.summonerProfile("hideonbush", "KR", "br1"));
    assertEquals(404, e.upstreamStatus);
    assertEquals(
        "Conta \"HIDEONBUSH#KR\" não encontrada — confira nome, tag e região (?platform=).",
        e.getMessage());
  }

  @Test
  @DisplayName("não-404 na resolução da conta (ex.: 429) propaga e NÃO carrega o cache")
  void erro429NaContaPropagaSemCachear() throws Exception {
    when(client.getRegional(
            anyString(),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            any(),
            any()))
        .thenThrow(new RiotApiException(429, "upstream 429"));

    var e1 =
        assertThrows(
            RiotApiException.class, () -> service.summonerProfile("hideonbush", "KR", "br1"));
    var e2 =
        assertThrows(RiotApiException.class, () -> service.masteries("hideonbush", "KR", "br1"));

    assertEquals(429, e1.upstreamStatus);
    assertEquals(429, e2.upstreamStatus);

    // nenhuma carga no cache: chave não foi carregada (o lock até criado, mas nada cacheado)
    assertEquals(0, mapaInterno("cache").size());
  }

  @Test
  @DisplayName("corrente: summonerProfile e masteries repetem a resolução da conta (DUPLICAÇÃO)")
  void contaReResolvidaEntreEndpoints() {
    stubFelicidade();

    service.summonerProfile("hideonbush", "KR", "br1");
    service.masteries("hideonbush", "KR", "br1");

    // CONTA UMA VEZ para cada endpoint: é a duplicata de Account-V1 que a refatoração vai extrair
    verify(client, times(2))
        .getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            eq("hideonbush"),
            eq("KR"));
    // summoner e ranked são do perfil: uma vez cada
    verify(client, times(1))
        .getServer(
            eq("br1"),
            contains("/lol/summoner/v4/summoners/by-puuid"),
            eq(RiotDtos.RiotSummoner.class),
            eq("puuid-1"));
    verify(client, times(1))
        .getServer(
            eq("br1"), contains("/league/v4/entries"), eq(RiotDtos.LeagueEntry[].class), any());
    // /top só no perfil; a lista completa só no masteries
    verify(client, times(1))
        .getServer(
            eq("br1"),
            contains("/champion-masteries/by-puuid"),
            anyMap(),
            eq(RiotDtos.MasteryDto[].class),
            eq("puuid-1"));
    verify(client, times(1))
        .getServer(
            eq("br1"),
            contains("/champion-masteries/by-puuid"),
            eq(RiotDtos.MasteryDto[].class),
            eq("puuid-1"));
  }

  @Test
  @DisplayName(
      "elo degradado (403) faz parte do perfil cacheado: dentro do TTL não se tenta de novo")
  void degradação403TempoCacheJunto() {
    stubFelicidade();
    // League com 403 (chave dev/personal): perfil segue, sem elo
    when(client.getServer(
            eq("br1"), contains("/league/v4/entries"), eq(RiotDtos.LeagueEntry[].class), any()))
        .thenThrow(new RiotApiException(403, "forbidden"));

    var perfil = service.summonerProfile("hideonbush", "KR", "br1");
    assertEquals(List.of(), perfil.ranked(), "403 degrada para sem-ranked");

    // segunda chamada dentro do TTL: NADA é refetchado (a degradação foi cacheada)
    service.summonerProfile("hideonbush", "KR", "br1");
    verify(client, times(1))
        .getServer(
            eq("br1"), contains("/league/v4/entries"), eq(RiotDtos.LeagueEntry[].class), any());
    verify(client, times(1))
        .getRegional(
            eq("br1"),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            any(),
            any());
  }

  @Test
  @DisplayName("corpo de rotação null: 404 \"Rotação indisponível.\"")
  void rotacaoNullVira404() {
    when(client.getServer(
            eq("br1"), contains("champion-rotations"), eq(RiotDtos.ChampionRotation.class)))
        .thenReturn(null);

    RiotApiException e = assertThrows(RiotApiException.class, () -> service.rotation("br1"));
    assertEquals(404, e.upstreamStatus);
    assertEquals("Rotação indisponível.", e.getMessage());
    assertInstanceOf(RiotApiException.class, e);
  }
}
