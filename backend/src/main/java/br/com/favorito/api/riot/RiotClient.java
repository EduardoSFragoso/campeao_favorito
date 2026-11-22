package br.com.favorito.api.riot;

import br.com.favorito.api.common.RiotApiException;
import br.com.favorito.api.config.RiotProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente da Riot API.
 *
 * <p>Duas "rotas" de host:
 *
 * <ul>
 *   <li>Server route (ex.: br1, na1, euw1): summoner, league, mastery, rotation, spectator.
 *   <li>Regional route (americas, europe, asia/sea): account-v1, match-v5.
 * </ul>
 *
 * Autenticação via header X-Riot-Token.
 */
@Component
public class RiotClient {

  private static final Logger log = LoggerFactory.getLogger(RiotClient.class);

  private static final Map<String, String> PLATFORM_TO_REGIONAL =
      Map.ofEntries(
          // Americas
          Map.entry("br1", "americas"),
          Map.entry("la1", "americas"),
          Map.entry("la2", "americas"),
          Map.entry("na1", "americas"),
          // Europe
          Map.entry("eun1", "europe"),
          Map.entry("euw1", "europe"),
          Map.entry("tr1", "europe"),
          Map.entry("ru", "europe"),
          // Asia / SEA
          Map.entry("jp1", "asia"),
          Map.entry("kr", "asia"),
          Map.entry("oc1", "sea"),
          Map.entry("ph2", "sea"),
          Map.entry("sg2", "sea"),
          Map.entry("th2", "sea"),
          Map.entry("tw2", "sea"),
          Map.entry("vn2", "sea"));

  private final String apiKey;
  private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

  public RiotClient(RiotProperties props) {
    this.apiKey = props.key() == null ? "" : props.key();
  }

  public boolean isConfigured() {
    return !apiKey.isBlank();
  }

  public record Routing(String platform, String serverHost, String regionalHost) {}

  public Routing routing(String platform) {
    String plat = (platform == null || platform.isBlank()) ? "br1" : platform.toLowerCase();
    return new Routing(
        plat,
        "https://" + plat + ".api.riotgames.com",
        "https://" + PLATFORM_TO_REGIONAL.getOrDefault(plat, "americas") + ".api.riotgames.com");
  }

  public <T> T getServer(String platform, String path, Class<T> type, Object... uriVars) {
    return get(routing(platform).serverHost(), path, null, type, uriVars);
  }

  public <T> T getServer(
      String platform, String path, Map<String, Object> query, Class<T> type, Object... uriVars) {
    return get(routing(platform).serverHost(), path, query, type, uriVars);
  }

  public <T> T getRegional(String platform, String path, Class<T> type, Object... uriVars) {
    return get(routing(platform).regionalHost(), path, null, type, uriVars);
  }

  private <T> T get(
      String baseUrl, String path, Map<String, Object> query, Class<T> type, Object... uriVars) {
    RestClient client =
        clients.computeIfAbsent(
            baseUrl,
            base ->
                RestClient.builder().baseUrl(base).defaultHeader("X-Riot-Token", apiKey).build());
    try {
      return client
          .get()
          .uri(b -> {
            b.path(path);
            if (query != null) {
              query.forEach(b::queryParam);
            }
            return b.build(uriVars);
          })
          .retrieve()
          .body(type);
    } catch (RestClientResponseException e) {
      log.warn("Riot API {} {} -> HTTP {}", baseUrl, path, e.getStatusCode().value());
      throw new RiotApiException(e.getStatusCode().value(), describe(e.getStatusCode().value()));
    }
  }

  private String describe(int status) {
    return switch (status) {
      case 400 -> "Requisição inválida para a Riot API.";
      case 403 ->
          "Chave da Riot API inválida ou expirada — gere uma nova no developer.riotgames.com.";
      case 404 -> "Não encontrado na Riot API.";
      case 429 -> "Rate limit da Riot API atingido — tenta de novo em instantes.";
      default -> "Riot API retornou HTTP " + status + ".";
    };
  }
}
