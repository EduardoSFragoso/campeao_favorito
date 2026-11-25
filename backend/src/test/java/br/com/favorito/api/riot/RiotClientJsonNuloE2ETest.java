package br.com.favorito.api.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.favorito.api.config.RiotProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste de ponta a ponta da leitura de JSON com null em campos numéricos: o corpo passa pela
 * resposta HTTP real do RestClient do RiotClient (mesmos conversores usados em runtime), não por um
 * mapper isolado como no {@link RiotDtosNulosTest}. Complementa essa cobertura fechando a pendência
 * "teste e2e do RiotClient com JSON nulo".
 *
 * <p>Caracterização do comportamento atual: null em numérico vira padrão (0) e null em lista vira
 * vazia — o teste falha se essa tolerância sumir dos conversores usados pelo cliente.
 */
class RiotClientJsonNuloE2ETest {

  private HttpServer servidor;
  private String base;
  private final RiotClient client = new RiotClient(new RiotProperties(null, "br1"));

  @BeforeEach
  void subirServidorDeStub() throws Exception {
    servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    servidor.createContext(
        "/rotacao",
        troca ->
            responder(
                troca,
                "{\"maxNewPlayerLevel\": null, "
                    + "\"freeChampionIds\": null, \"freeChampionIdsForNewPlayers\": [7, 19]}"));
    servidor.createContext(
        "/summoner",
        troca ->
            responder(
                troca,
                "{\"id\": \"s1\", \"puuid\": \"p1\", \"name\": \"Joao\", \"accountId\": \"a1\","
                    + " \"profileIconId\": null, \"summonerLevel\": null, \"revisionDate\": null}"));
    servidor.createContext(
        "/entradas",
        troca ->
            responder(
                troca,
                "[{\"queueType\": \"RANKED_SOLO_5x5\", \"tier\": \"SILVER\", \"rank\": \"II\","
                    + " \"leaguePoints\": null, \"wins\": null, \"losses\": null,"
                    + " \"veteran\": null, \"inactive\": false, \"freshBlood\": false}]"));
    servidor.createContext(
        "/maestria",
        troca ->
            responder(
                troca,
                "[{\"championId\": null, \"championLevel\": null, \"championPoints\": null,"
                    + " \"chestGranted\": null, \"lastPlayTime\": null}]"));
    servidor.start();
    base = "http://127.0.0.1:" + servidor.getAddress().getPort();
  }

  private static void responder(com.sun.net.httpserver.HttpExchange troca, String corpo)
      throws java.io.IOException {
    byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
    troca.getResponseHeaders().set("Content-Type", "application/json");
    troca.sendResponseHeaders(200, bytes.length);
    try (var out = troca.getResponseBody()) {
      out.write(bytes);
    }
    troca.close();
  }

  @AfterEach
  void derrubarServidor() {
    if (servidor != null) {
      servidor.stop(0);
    }
  }

  @Test
  @DisplayName("ChampionRotation via HTTP real: null em numérico → 0, null em lista → vazia")
  void rotacaoComNulosPeloRestClientReal() {
    RiotDtos.ChampionRotation rotacao =
        client.get(base, "/rotacao", null, RiotDtos.ChampionRotation.class);

    assertEquals(0, rotacao.maxNewPlayerLevel());
    assertTrue(rotacao.freeChampionIds().isEmpty(), "lista null vira vazia, nunca null");
    assertEquals(java.util.List.of(7, 19), rotacao.freeChampionIdsForNewPlayers());
  }

  @Test
  @DisplayName("RiotSummoner via HTTP real: profileIconId/summonerLevel/revisionDate null → 0")
  void summonerComNulosPeloRestClientReal() {
    RiotDtos.RiotSummoner summoner =
        client.get(base, "/summoner", null, RiotDtos.RiotSummoner.class);

    assertEquals(0, summoner.profileIconId());
    assertEquals(0L, summoner.summonerLevel());
    assertEquals(0L, summoner.revisionDate());
    assertEquals("s1", summoner.id());
    assertEquals("Joao", summoner.name());
  }

  @Test
  @DisplayName("LeagueEntry[] via HTTP real: leaguePoints/wins/losses null → 0 e helpers ok")
  void entradasDeLigaComNulosPeloRestClientReal() {
    RiotDtos.LeagueEntry[] entradas =
        client.get(base, "/entradas", null, RiotDtos.LeagueEntry[].class);

    assertEquals(1, entradas.length);
    assertEquals(0, entradas[0].leaguePoints());
    assertEquals(0, entradas[0].wins());
    assertEquals(0, entradas[0].losses());
    assertEquals(0, entradas[0].games());
    assertEquals(0.0, entradas[0].winratePercent());
    assertNull(entradas[0].veteran(), "Boolean wrapper null chega null (sem default forçado)");
  }

  @Test
  @DisplayName("MasteryDto[] via HTTP real: championId/championLevel/championPoints null → 0")
  void maestriaComNulosPeloRestClientReal() {
    RiotDtos.MasteryDto[] masteries =
        client.get(base, "/maestria", null, RiotDtos.MasteryDto[].class);

    assertEquals(1, masteries.length);
    assertEquals(0, masteries[0].championId());
    assertEquals(0, masteries[0].championLevel());
    assertEquals(0, masteries[0].championPoints());
    assertNull(masteries[0].chestGranted(), "campo da Riot removido (baú) chega null sem estourar");
    assertNull(masteries[0].lastPlayTime());
  }
}
