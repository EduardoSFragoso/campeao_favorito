package br.com.favorito.api.riot;

import br.com.favorito.api.common.EntradaInvalidaException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrada validada antes de virar parâmetro de rota da Riot (gameName/tagLine limitados a um
 * subconjunto de unicode-safe; a <em>platform</em> é validada como whitelista em {@link
 * RiotClient#routing} porque entra na construção do host).
 */
@RestController
@RequestMapping("/api/riot")
@RequiredArgsConstructor
@Validated
@Tag(
    name = "Riot API",
    description = "Dados de invocador, elo, maestria e rotação via Riot Games API")
public class RiotController {

  private final RiotService service;
  private final br.com.favorito.api.config.RiotProperties props;
  private final RiotRateLimiter rateLimiter;

  /**
   * 10 req/min por usuário autenticado (chave = id do token) MAIS um limite GLOBAL de 40/min para
   * todas as rotas /api/riot/** juntos. Testes de slice autenticam via post processor; em prod, o
   * JwtAuthFilter constrói o token com o id como principal.
   */
  private void limitarPorUsuario() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    rateLimiter.check(String.valueOf(auth.getName()));
  }

  @Operation(summary = "Perfil de invocador: nível, elo ranqueado e top 5 de maestria")
  @GetMapping("/summoner")
  public RiotDtos.SummonerProfile summoner(
      @RequestParam
          @NotBlank
          @Pattern(
              regexp = "(?=.*[\\p{L}\\p{N}])[\\p{L}\\p{N} ._'\\-]{1,32}",
              message =
                  "1–32 caracteres: letras, números, espaço, . _ ' -; ao menos um alfanumérico")
          String gameName,
      @RequestParam
          @NotBlank
          @Pattern(regexp = "[\\p{L}\\p{N}]{2,5}", message = "2–5 letras ou números")
          String tagLine,
      @RequestParam(required = false) String platform) {
    limitarPorUsuario();
    return service.summonerProfile(gameName, tagLine, platformOr(platform));
  }

  @Operation(summary = "Rotação semanal de campeões gratuitos")
  @GetMapping("/rotation")
  public RiotDtos.ChampionRotation rotation(@RequestParam(required = false) String platform) {
    limitarPorUsuario();
    return service.rotation(platformOr(platform));
  }

  @Operation(summary = "Maestria completa de conta: nível de cada campeão (para badge no grid)")
  @GetMapping("/masteries")
  public java.util.List<RiotDtos.MasteryDto> masteries(
      @RequestParam
          @NotBlank
          @Pattern(
              regexp = "(?=.*[\\p{L}\\p{N}])[\\p{L}\\p{N} ._'\\-]{1,32}",
              message =
                  "1–32 caracteres: letras, números, espaço, . _ ' -; ao menos um alfanumérico")
          String gameName,
      @RequestParam
          @NotBlank
          @Pattern(regexp = "[\\p{L}\\p{N}]{2,5}", message = "2–5 letras ou números")
          String tagLine,
      @RequestParam(required = false) String platform) {
    limitarPorUsuario();
    return service.masteries(gameName, tagLine, platformOr(platform));
  }

  private String platformOr(String platform) {
    String p = (platform == null || platform.isBlank()) ? props.defaultPlatform() : platform;
    if (p == null || p.isBlank()) {
      throw new EntradaInvalidaException("Informar ?platform=");
    }
    return p;
  }
}
