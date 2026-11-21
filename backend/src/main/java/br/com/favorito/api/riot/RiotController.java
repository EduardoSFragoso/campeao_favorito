package br.com.favorito.api.riot;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/riot")
@RequiredArgsConstructor
@Tag(
    name = "Riot API",
    description = "Dados de invocador, elo, maestria e rotação via Riot Games API")
public class RiotController {

  private final RiotService service;
  private final br.com.favorito.api.config.RiotProperties props;

  @Operation(summary = "Perfil de invocador: nível, elo ranqueado e top 5 de maestria")
  @GetMapping("/summoner")
  public RiotDtos.SummonerProfile summoner(
      @RequestParam String gameName,
      @RequestParam String tagLine,
      @RequestParam(required = false) String platform) {
    return service.summonerProfile(gameName, tagLine, platformOr(platform));
  }

  @Operation(summary = "Rotação semanal de campeões gratuitos")
  @GetMapping("/rotation")
  public RiotDtos.ChampionRotation rotation(@RequestParam(required = false) String platform) {
    return service.rotation(platformOr(platform));
  }

  private String platformOr(String platform) {
    String p = (platform == null || platform.isBlank()) ? props.defaultPlatform() : platform;
    if (p == null || p.isBlank()) {
      throw new IllegalArgumentException("Informar ?platform=");
    }
    return p;
  }
}
