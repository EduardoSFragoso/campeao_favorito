package br.com.favorito.api.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.favorito.api.common.EntradaInvalidaException;
import br.com.favorito.api.config.RiotProperties;
import org.junit.jupiter.api.Test;

/** A platform entra na construção do host da URL — a whitelist do routing é barreira de SSRF. */
class RiotClientTest {

  private final RiotClient client = new RiotClient(new RiotProperties(null, "br1"));

  @Test
  void routingDerivaHostDeServerERegional() {
    RiotClient.Routing br = client.routing("BR1"); // case-insensitive
    assertEquals("br1", br.platform());
    assertEquals("https://br1.api.riotgames.com", br.serverHost());
    assertEquals("https://americas.api.riotgames.com", br.regionalHost());

    assertEquals("https://kr.api.riotgames.com", client.routing("kr").serverHost());
    assertEquals("https://asia.api.riotgames.com", client.routing("kr").regionalHost());
  }

  @Test
  void platformVaziaCaiNoDefault() {
    assertEquals("br1", client.routing(null).platform());
    assertEquals("br1", client.routing(" ").platform());
  }

  @Test
  void rejeitaPlataformaForaDaWhiteList() {
    // tentativa clássica de SSRF: host externo colado no prefixo (EntradaInvalidaException → 400
    // com a mensagem acionável no handler; é validação que nós controlamos)
    assertThrows(EntradaInvalidaException.class, () -> client.routing("evil.com/"));
    assertThrows(EntradaInvalidaException.class, () -> client.routing("../internal"));
    assertThrows(EntradaInvalidaException.class, () -> client.routing("xx1"));
  }

  @Test
  void todaPlataformaMapeadaTemHostValido() {
    for (String plat : RiotClient.platforms()) {
      RiotClient.Routing r = client.routing(plat);
      assertTrue(
          r.serverHost().startsWith("https://") && r.serverHost().endsWith(".api.riotgames.com"));
      assertTrue(
          r.regionalHost().startsWith("https://")
              && r.regionalHost().endsWith(".api.riotgames.com"));
    }
  }
}
