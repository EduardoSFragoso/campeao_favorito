package br.com.favorito.api.favorites;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@Tag(name = "Favoritos", description = "Campeões favoritos do usuário logado")
public class FavoriteController {

  private final FavoriteService service;

  @Operation(summary = "Lista os favoritos do usuário autenticado")
  @GetMapping
  public List<FavoriteDtos.FavoriteResponse> list() {
    return service.listMine().stream().map(FavoriteDtos.FavoriteResponse::de).toList();
  }

  @Operation(summary = "Favorita um campeão")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FavoriteDtos.FavoriteResponse add(@Valid @RequestBody FavoriteDtos.FavoriteRequest req) {
    return FavoriteDtos.FavoriteResponse.de(service.add(req));
  }

  @Operation(summary = "Remove um campeão dos favoritos")
  @DeleteMapping("/{championId}")
  public Map<String, Boolean> remove(@PathVariable String championId) {
    service.remove(championId);
    return Map.of("removed", true);
  }
}
