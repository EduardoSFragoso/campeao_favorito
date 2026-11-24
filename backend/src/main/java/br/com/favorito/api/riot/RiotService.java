package br.com.favorito.api.riot;

import br.com.favorito.api.common.RiotApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agrega Account-V1 + Summoner-V4 + League-V4 + Champion Mastery-V4. Cache simples em memória com
 * TTL — a Riot cobra rate limit caro (dev key: 20 req/s e 100 req/2min) e o perfil não muda por
 * segundo. Cache e locks têm teto ({@link #MAX_ENTRADAS}): chaves não eventualmente expiradas são
 * descartadas da mais antiga para a mais nova (ordem de inserção via sequência).
 */
@Service
@RequiredArgsConstructor
public class RiotService {

  private static final Logger log = LoggerFactory.getLogger(RiotService.class);

  private static final long PROFILE_TTL_MS = 60_000;
  private static final long ROTATION_TTL_MS = 5 * 60_000;

  /** Teto de cache e de locks — além disso, sai o mais antigo (são mapas em memória, sem dump). */
  static final int MAX_ENTRADAS = 1000;

  private record Cached<T>(T value, long expiresAt, long sequencia) {}

  private record Cadeado(ReentrantLock lock, long sequencia) {}

  private final RiotClient client;
  private final Map<String, Cached<Object>> cache = new ConcurrentHashMap<>();
  private final Map<String, Cadeado> locks = new ConcurrentHashMap<>();

  /** Ordem de inserção de cache/locks — a fila dos evictados quando o teto estoura. */
  private final AtomicLong sequencia = new AtomicLong();

  private final AtomicReference<Cached<RiotDtos.ChampionRotation>> rotationCache =
      new AtomicReference<>();

  @SuppressWarnings("unchecked")
  private <T> T withCache(String key, java.util.function.Supplier<T> loader) {
    Cached<Object> hit = cache.get(key);
    if (hit != null && hit.expiresAt() > System.currentTimeMillis()) {
      return (T) hit.value();
    }
    // lock por chave para não furar o rate limit com buscas repetidas
    ReentrantLock lock = cadeadoPara(key).lock();
    lock.lock();
    try {
      hit = cache.get(key);
      if (hit != null && hit.expiresAt() > System.currentTimeMillis()) {
        return (T) hit.value();
      }
      T value = loader.get();
      guardarNoCache(key, value);
      return value;
    } finally {
      lock.unlock();
    }
  }

  private void guardarNoCache(String key, Object value) {
    cache.put(
        key,
        new Cached<>(
            value, System.currentTimeMillis() + PROFILE_TTL_MS, sequencia.incrementAndGet()));
    if (cache.size() <= MAX_ENTRADAS) {
      return;
    }
    // estourou o teto: primeiro vai embora o que já expirou mesmo; o que faltar, sai o mais antigo
    long agora = System.currentTimeMillis();
    cache.values().removeIf(c -> c.expiresAt() <= agora);
    while (cache.size() > MAX_ENTRADAS) {
      String maisAntiga = null;
      long menor = Long.MAX_VALUE;
      for (var e : cache.entrySet()) {
        if (e.getValue().sequencia() < menor) {
          menor = e.getValue().sequencia();
          maisAntiga = e.getKey();
        }
      }
      cache.remove(maisAntiga);
    }
  }

  private Cadeado cadeadoPara(String key) {
    Cadeado atual =
        locks.computeIfAbsent(
            key, k -> new Cadeado(new ReentrantLock(), sequencia.incrementAndGet()));
    if (locks.size() > MAX_ENTRADAS) {
      // Enxuga o mais antigo. No pior caso (duas threads na mesma chave com o objeto novo e o
      // evictado) perde-se a exclusão mútua daquela chave por uma rodada — a consequência é só um
      // request extra ao upstream, nunca dado errado.
      String maisAntiga = null;
      long menor = Long.MAX_VALUE;
      for (var e : locks.entrySet()) {
        if (e.getValue().sequencia() < menor) {
          menor = e.getValue().sequencia();
          maisAntiga = e.getKey();
        }
      }
      locks.remove(maisAntiga);
    }
    return atual;
  }

  /**
   * Mesmas regras dos {@code @Pattern} do controller — mas aqui dentro, e ANTES de qualquer uso de
   * cache/locks: o valor vai para a chave dos dois, e a platform para a URL da Riot.Uma entrada
   * inválida não pode abrir lock nem deixar resíduo nos mapas.
   */
  private static String exigirTextoValido(String valor, String mensagem) {
    String v = valor == null ? "" : valor.trim();
    if (v.isBlank() || !v.matches("[\\p{L}\\p{N} ._'\\-]{1,32}")) {
      throw new IllegalArgumentException(mensagem);
    }
    return v;
  }

  private static String exigirTagValida(String tagLine) {
    String t = tagLine == null ? "" : tagLine.trim();
    if (t.isBlank() || !t.matches("[\\p{L}\\p{N}]{2,5}")) {
      throw new IllegalArgumentException("tagLine inválida: 2–5 letras ou números.");
    }
    return t;
  }

  private static void exigirPlataformaValida(String platform) {
    String p = (platform == null || platform.isBlank()) ? "br1" : platform.trim().toLowerCase();
    if (!RiotClient.platforms().contains(p)) {
      throw new IllegalArgumentException("Plataforma inválida: " + p);
    }
  }

  public RiotDtos.SummonerProfile summonerProfile(
      String gameName, String tagLine, String platform) {
    String gn =
        exigirTextoValido(gameName, "gameName: 2–32 caracteres: letras, números, espaço, . _ ' -");
    String tl = exigirTagValida(tagLine);
    exigirPlataformaValida(platform);
    String key = gn.toUpperCase() + "#" + tl.toUpperCase();

    return withCache(
        "profile:" + platform + ":" + key,
        () -> {
          RiotDtos.RiotAccount account;
          try {
            account =
                client.getRegional(
                    platform,
                    "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}",
                    RiotDtos.RiotAccount.class,
                    gn,
                    tl);
          } catch (RiotApiException e) {
            if (e.upstreamStatus == 404) {
              throw new RiotApiException(
                  404,
                  "Conta \""
                      + key
                      + "\" não encontrada — confira nome, tag e região (?platform=).");
            }
            throw e;
          }
          if (account == null || account.puuid() == null) {
            throw new RiotApiException(
                404,
                "Conta \"" + key + "\" não encontrada — confira nome, tag e região (?platform=).");
          }

          RiotDtos.RiotSummoner summoner;
          try {
            summoner =
                client.getServer(
                    platform,
                    "/lol/summoner/v4/summoners/by-puuid/{puuid}",
                    RiotDtos.RiotSummoner.class,
                    account.puuid());
          } catch (RiotApiException e) {
            if (e.upstreamStatus == 404) {
              // a conta existe na Riot (Account-V1 achou o PUUID), mas não tem perfil de
              // LoL naquele servidor — diferente de "conta não encontrada" (que falha acima)
              throw new RiotApiException(
                  404,
                  "Conta \""
                      + key
                      + "\" existe, mas não tem LoL no servidor \""
                      + platform
                      + "\". Confira a região (?platform=).");
            }
            throw e;
          }

          List<RiotDtos.LeagueEntry> ranked =
              withCache(
                  "ranked:" + summoner.id(),
                  () -> {
                    try {
                      RiotDtos.LeagueEntry[] arr =
                          client.getServer(
                              platform,
                              "/lol/league/v4/entries/by-summoner/{sid}",
                              RiotDtos.LeagueEntry[].class,
                              summoner.id());
                      return arr == null
                          ? List.<RiotDtos.LeagueEntry>of()
                          : List.of(arr).stream().filter(e -> e.games() > 0).toList();
                    } catch (RiotApiException e) {
                      // League-V4 vem com 403 para chaves de dev/pessoais — degrada sem elo
                      // em vez de falhar o perfil inteiro. Com chave de produção volta a mostrar.
                      if (e.upstreamStatus == 403 || e.upstreamStatus == 404) {
                        log.warn(
                            "Elo indisponível para {} na {} (HTTP {}) — perfil sem ranqueada.",
                            key,
                            platform,
                            e.upstreamStatus);
                        return List.<RiotDtos.LeagueEntry>of();
                      }
                      throw e;
                    }
                  });

          List<RiotDtos.MasteryDto> mastery =
              withCache(
                  "mastery:" + account.puuid(),
                  () -> {
                    Map<String, Object> query = new HashMap<>();
                    query.put("count", 5);
                    RiotDtos.MasteryDto[] arr =
                        client.getServer(
                            platform,
                            "/lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}/top",
                            query,
                            RiotDtos.MasteryDto[].class,
                            account.puuid());
                    return arr == null ? List.of() : List.of(arr);
                  });

          return new RiotDtos.SummonerProfile(
              account.puuid(),
              account.gameName(),
              account.tagLine(),
              summoner.name(),
              summoner.profileIconId(),
              summoner.summonerLevel(),
              ranked,
              mastery);
        });
  }

  /**
   * Maestria COMPLETA por campeão (Champion Mastery-V4 sem /top). Usada pelo grid para badge de
   * nível de maestria. Resolve a conta igual ao perfil (mesmo tratamento de 404), cacheia por conta
   * — o payload só muda após partidas jogadas.
   */
  public List<RiotDtos.MasteryDto> masteries(String gameName, String tagLine, String platform) {
    String gn =
        exigirTextoValido(gameName, "gameName: 2–32 caracteres: letras, números, espaço, . _ ' -");
    String tl = exigirTagValida(tagLine);
    exigirPlataformaValida(platform);
    String key = gn.toUpperCase() + "#" + tl.toUpperCase();

    return withCache(
        "masteryAll:" + platform + ":" + key,
        () -> {
          RiotDtos.RiotAccount account;
          try {
            account =
                client.getRegional(
                    platform,
                    "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}",
                    RiotDtos.RiotAccount.class,
                    gn,
                    tl);
          } catch (RiotApiException e) {
            if (e.upstreamStatus == 404) {
              throw new RiotApiException(
                  404,
                  "Conta \""
                      + key
                      + "\" não encontrada — confira nome, tag e região (?platform=).");
            }
            throw e;
          }
          if (account == null || account.puuid() == null) {
            throw new RiotApiException(
                404,
                "Conta \"" + key + "\" não encontrada — confira nome, tag e região (?platform=).");
          }

          RiotDtos.MasteryDto[] arr =
              client.getServer(
                  platform,
                  "/lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}",
                  RiotDtos.MasteryDto[].class,
                  account.puuid());
          return arr == null ? List.<RiotDtos.MasteryDto>of() : List.of(arr);
        });
  }

  public RiotDtos.ChampionRotation rotation(String platform) {
    exigirPlataformaValida(platform);
    Cached<RiotDtos.ChampionRotation> hit = rotationCache.get();
    if (hit != null && hit.expiresAt() > System.currentTimeMillis()) {
      return hit.value();
    }
    RiotDtos.ChampionRotation rotation =
        client.getServer(
            platform, "/lol/platform/v3/champion-rotations", RiotDtos.ChampionRotation.class);
    if (rotation == null) {
      throw new RiotApiException(404, "Rotação indisponível.");
    }
    rotationCache.set(
        new Cached<>(
            rotation, System.currentTimeMillis() + ROTATION_TTL_MS, sequencia.incrementAndGet()));
    return rotation;
  }
}
