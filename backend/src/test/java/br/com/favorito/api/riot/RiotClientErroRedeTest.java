package br.com.favorito.api.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.favorito.api.common.RiotApiException;
import br.com.favorito.api.config.RiotProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Falha de rede/leitura no cliente da Riot (conexão cortada, corpo ilegível) não pode cair no
 * catch-all como 500: vira RiotApiException de 502 com mensagem GENÉRICA — sem Corps nem stack da
 * resposta no payload do erro. Usa um servidor HTTP local que fecha a conexão sem responder (a URL
 * da Riot é fixa; o método get é package-private justamente para este teste de costura).
 */
class RiotClientErroRedeTest {

  private HttpServer servidor;
  private String base;
  private final RiotClient client = new RiotClient(new RiotProperties(null, "br1"));

  @BeforeEach
  void subirServidorQueCortaConexao() throws Exception {
    servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    servidor.createContext(
        "/corta",
        exchange -> {
          // fecha IMEDIATAMENTE, sem status nem corpo — simula conexão caindo
          exchange.close();
        });
    servidor.createContext(
        "/corpo-torto",
        exchange -> {
          byte[] corpo = "nao sou json".getBytes();
          exchange.getResponseHeaders().set("Content-Type", "text/plain");
          exchange.sendResponseHeaders(200, corpo.length);
          try (var out = exchange.getResponseBody()) {
            out.write(corpo);
          }
          exchange.close();
        });
    servidor.start();
    base = "http://127.0.0.1:" + servidor.getAddress().getPort();
  }

  @AfterEach
  void derrubarServidor() {
    if (servidor != null) {
      servidor.stop(0);
    }
  }

  @Test
  @DisplayName("conexão cortada na leitura → RiotApiException 502, não exceção crua")
  void leituraDeCorpoFalhaViraRiotApiException502() {
    RiotApiException e =
        assertThrows(
            RiotApiException.class,
            () -> client.get(base, "/corta", null, RiotDtos.ChampionRotation.class),
            "falha de rede precisa converter em RiotApiException");

    assertEquals(502, e.upstreamStatus, "erro de rede downstream → 502 Bad Gateway");
  }

  @Test
  @DisplayName("corpo ilegível na leitura → RiotApiException 502 com mensagem genérica")
  void corpoIlegivelViraRiotApiException502SemCorpoNoErro() {
    RiotApiException e =
        assertThrows(
            RiotApiException.class,
            () -> client.get(base, "/corpo-torto", null, RiotDtos.ChampionRotation.class),
            "corpo não convertível precisa converter em RiotApiException");

    assertEquals(502, e.upstreamStatus);
    assertEquals("Riot API indisponível ou resposta ilegível.", e.getMessage());
  }
}
