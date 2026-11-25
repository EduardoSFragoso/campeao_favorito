package br.com.favorito.api.riot;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.List;

/** DTOs das respostas da Riot API (campos que usamos). */
public final class RiotDtos {

  private RiotDtos() {}

  // Account-V1 (rota regional: americas/europe/asia)
  public record RiotAccount(String puuid, String gameName, String tagLine) {}

  // Summoner-V4 (rota de servidor, ex.: br1). Numéricos toleram null → 0 (AS_EMPTY): a Riot
  // já mandou null em campos numéricos e o Jackson 3 do Boot recusa null em primitivo.
  public record RiotSummoner(
      String id,
      String puuid,
      String name,
      String accountId,
      @JsonSetter(nulls = Nulls.AS_EMPTY) int profileIconId,
      @JsonSetter(nulls = Nulls.AS_EMPTY) long summonerLevel,
      @JsonSetter(nulls = Nulls.AS_EMPTY) long revisionDate) {}

  // League-V4 (rota de servidor)
  public record LeagueEntry(
      String queueType,
      String tier,
      String rank,
      @JsonSetter(nulls = Nulls.AS_EMPTY) int leaguePoints,
      @JsonSetter(nulls = Nulls.AS_EMPTY) int wins,
      @JsonSetter(nulls = Nulls.AS_EMPTY) int losses,
      Boolean veteran,
      Boolean inactive,
      Boolean freshBlood) {
    public int games() {
      return wins + losses;
    }

    public double winratePercent() {
      return games() == 0 ? 0 : (wins * 100.0) / games();
    }
  }

  // Champion Mastery-V4 (rota de servidor).
  // Campos em wrapper (Boolean/Long) toleram ausência: a Riot removeu chestGranted
  // (baús fim de 2025) e segue adicionando campos (nextSeasonMilestone etc.).
  @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
  public record MasteryDto(
      @JsonSetter(nulls = Nulls.AS_EMPTY) int championId,
      @JsonSetter(nulls = Nulls.AS_EMPTY) int championLevel,
      @JsonSetter(nulls = Nulls.AS_EMPTY) int championPoints,
      Boolean chestGranted,
      Long lastPlayTime) {}

  // Champion-V3 — rotação semanal (rota de servidor). Listas null → vazias (AS_EMPTY).
  public record ChampionRotation(
      @JsonSetter(nulls = Nulls.AS_EMPTY) int maxNewPlayerLevel,
      @JsonSetter(nulls = Nulls.AS_EMPTY) List<Integer> freeChampionIds,
      @JsonSetter(nulls = Nulls.AS_EMPTY) List<Integer> freeChampionIdsForNewPlayers) {}

  // Resposta agregada do endpoint /api/riot/summoner (montado pelo RiotService em Java; AS_EMPTY
  // só por uniformidade, caso um dia seja lido de JSON).
  public record SummonerProfile(
      String puuid,
      String gameName,
      String tagLine,
      String name,
      @JsonSetter(nulls = Nulls.AS_EMPTY) int profileIconId,
      @JsonSetter(nulls = Nulls.AS_EMPTY) long summonerLevel,
      List<LeagueEntry> ranked,
      List<MasteryDto> topMasteries) {}
}
