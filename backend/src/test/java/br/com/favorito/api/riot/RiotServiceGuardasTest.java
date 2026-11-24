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
import static org.mockito.Mockito.when;

import br.com.favorito.api.common.RiotApiException;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardas da memória do {@link RiotService}: entradas do cache/locks não podem crescer sem limite
 * (usernames de atacante geram chave nova por request) e a validação de platform/gameName/tagLine
 * tem de acontecer ANTES de tocar nos maps — entrada inválida não pode deixar lock nem chave no
 * cache (no código atual, a whitelist de platform só era consultada dentro do loader, depois do
 * lock criado).
 */
class RiotServiceGuardasTest {

  /** Teto combinado no PR (mesmo número da implementação). */
  private static final int MAX_ENTRADAS = 1000;

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

  private void stubRotaFelizIgualParaTodoMundo() {
    // conta/sumoner fixos: só o gameName muda, então só a chave "profile:" cresce
    when(client.getRegional(
            anyString(),
            contains("/accounts/by-riot-id"),
            eq(RiotDtos.RiotAccount.class),
            any(),
            any()))
        .thenReturn(new RiotDtos.RiotAccount("puuid-1", "catan", "TST"));

    when(client.getServer(
            anyString(),
            contains("/lol/summoner/v4/summoners/by-puuid"),
            eq(RiotDtos.RiotSummoner.class),
            anyString()))
        .thenReturn(new RiotDtos.RiotSummoner("sid-1", "puuid-1", "catan", "acid-1", 42, 300, 0L));
    when(client.getServer(
            anyString(),
            contains("/league/v4/entries"),
            eq(RiotDtos.LeagueEntry[].class),
            anyString()))
        .thenThrow(new RiotApiException(403, "sem chave (degrada sem elo)"));
    when(client.getServer(
            anyString(),
            contains("/champion-masteries/by-puuid"),
            anyMap(),
            eq(RiotDtos.MasteryDto[].class),
            anyString()))
        .thenReturn(new RiotDtos.MasteryDto[0]);
  }

  @Test
  @DisplayName("muitas chaves distintas: cache e locks ficam no teto (evicted as mais antigas)")
  void muitasChavesDistintasNaoCrescemSemLimite() throws Exception {
    stubRotaFelizIgualParaTodoMundo();

    // passar do teto (2 entradas de suporte + 1 profile por key)
    for (int i = 0; i < MAX_ENTRADAS + 100; i++) {
      service.summonerProfile("usuario" + i, "tst", "br1");
    }

    var cache = mapaInterno("cache");
    var locks = mapaInterno("locks");

    // o reino de gameNames atacantes não pode explodir a heap
    assertEquals(true, cache.size() <= MAX_ENTRADAS, "cache passou do teto: " + cache.size());
    assertEquals(true, locks.size() <= MAX_ENTRADAS, "locks passou do teto: " + locks.size());
  }

  @Test
  @DisplayName("platform fora da whitelist: 400 SEM criar lock ou entrada no cache")
  void platformInvalidaNaoCriaLockNemEntradaDeCache() throws Exception {
    var e =
        assertThrows(
            Exception.class,
            () -> service.summonerProfile("Hideonbush", "KR", "plataforma-fantasma"));
    assertInstanceOf(IllegalArgumentException.class, e);

    // nenhum traço nos maps: a guarda roda antes de qualquer uso deles
    assertEquals(0, mapaInterno("locks").size());
    assertEquals(0, mapaInterno("cache").size());
  }

  @Test
  @DisplayName("gameName com caractere proibido: 400 antes de tocar em cache/locks")
  void gameNameInvalidoNaoTocaNosMaps() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> service.summonerProfile("na(me)rua", "KR", "br1"));
    assertEquals(0, mapaInterno("locks").size());
    assertEquals(0, mapaInterno("cache").size());
  }

  @Test
  @DisplayName("tagLine com formato inválido: 400 antes de tocar em cache/locks")
  void tagLineInvalidaNaoTocaNosMaps() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> service.masteries("Hideonbush", "TOO-LONG", "br1"));
    assertEquals(0, mapaInterno("locks").size());
    assertEquals(0, mapaInterno("cache").size());
  }
}
