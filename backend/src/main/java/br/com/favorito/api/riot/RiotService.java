package br.com.favorito.api.riot;

import br.com.favorito.api.common.RiotApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agrega Account-V1 + Summoner-V4 + League-V4 + Champion Mastery-V4. Cache simples em memória com
 * TTL — a Riot cobra rate limit caro (dev key: 20 req/s e 100 req/2min) e o perfil não muda por
 * segundo.
 */
@Service
@RequiredArgsConstructor
public class RiotService {

  private static final Logger log = LoggerFactory.getLogger(RiotService.class);

  private static final long PROFILE_TTL_MS = 60_000;
  private static final long ROTATION_TTL_MS = 5 * 60_000;

  private record Cached<T>(T value, long expiresAt) {}

  private final RiotClient client;
  private final Map<String, Cached<Object>> cache = new ConcurrentHashMap<>();
  private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
  private final AtomicReference<Cached<RiotDtos.ChampionRotation>> rotationCache =
      new AtomicReference<>();

  @SuppressWarnings("unchecked")
  private <T> T withCache(String key, java.util.function.Supplier<T> loader) {
    Cached<Object> hit = cache.get(key);
    if (hit != null && hit.expiresAt() > System.currentTimeMillis()) {
      return (T) hit.value();
    }
    // lock por chave para não furar o rate limit com buscas repetidas
    ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
    lock.lock();
    try {
      hit = cache.get(key);
      if (hit != null && hit.expiresAt() > System.currentTimeMillis()) {
        return (T) hit.value();
      }
      T value = loader.get();
      cache.put(key, new Cached<>(value, System.currentTimeMillis() + PROFILE_TTL_MS));
      return value;
    } finally {
      lock.unlock();
    }
  }

  public RiotDtos.SummonerProfile summonerProfile(
      String gameName, String tagLine, String platform) {
    String gn = gameName.trim();
    String tl = tagLine.trim();
    String key = gn.toUpperCase() + "#" + tl.toUpperCase();

    return withCache(
        "profile:" + platform + ":" + key,
        () -> {
          RiotDtos.RiotAccount account =
              client.getRegional(
                  platform,
                  "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}",
                  RiotDtos.RiotAccount.class,
                  gn,
                  tl);
          if (account == null || account.puuid() == null) {
            throw new RiotApiException(404, "Conta não encontrada: " + key);
          }

          RiotDtos.RiotSummoner summoner =
              client.getServer(
                  platform,
                  "/lol/summoner/v4/summoners/by-puuid/{puuid}",
                  RiotDtos.RiotSummoner.class,
                  account.puuid());

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

  public RiotDtos.ChampionRotation rotation(String platform) {
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
    rotationCache.set(new Cached<>(rotation, System.currentTimeMillis() + ROTATION_TTL_MS));
    return rotation;
  }
}
