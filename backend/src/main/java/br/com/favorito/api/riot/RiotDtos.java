package br.com.favorito.api.riot;

import java.util.List;

/** DTOs das respostas da Riot API (campos que usamos). */
public final class RiotDtos {

  private RiotDtos() {}

  // Account-V1 (rota regional: americas/europe/asia)
  public record RiotAccount(String puuid, String gameName, String tagLine) {}

  // Summoner-V4 (rota de servidor, ex.: br1)
  public record RiotSummoner(
      String id,
      String puuid,
      String name,
      String accountId,
      int profileIconId,
      long summonerLevel,
      long revisionDate) {}

  // League-V4 (rota de servidor)
  public record LeagueEntry(
      String queueType,
      String tier,
      String rank,
      int leaguePoints,
      int wins,
      int losses,
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

  // Champion Mastery-V4 (rota de servidor)
  public record MasteryDto(
      int championId,
      int championLevel,
      int championPoints,
      boolean chestGranted,
      long lastPlayTime) {}

  // Champion-V3 — rotação semanal (rota de servidor)
  public record ChampionRotation(
      int maxNewPlayerLevel,
      List<Integer> freeChampionIds,
      List<Integer> freeChampionIdsForNewPlayers) {}

  // Resposta agregada do endpoint /api/riot/summoner
  public record SummonerProfile(
      String puuid,
      String gameName,
      String tagLine,
      String name,
      int profileIconId,
      long summonerLevel,
      List<LeagueEntry> ranked,
      List<MasteryDto> topMasteries) {}
}
