package br.com.favorito.api.favorites;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record FavoriteDtos() {
  public record FavoriteRequest(
      @NotBlank @Size(max = 64) String championId,
      @NotBlank @Size(max = 64) String championName,
      @Size(max = 256) String title) {}

  /**
   * Saída do controller: só os campos públicos que o frontend consome (CfFavorite) — userId e
   * demais colunas internas da entidade não vazam no JSON.
   */
  public record FavoriteResponse(
      long id, String championId, String championName, String title, Instant createdAt) {
    public static FavoriteResponse de(Favorite f) {
      return new FavoriteResponse(
          f.getId(), f.getChampionId(), f.getChampionName(), f.getTitle(), f.getCreatedAt());
    }
  }
}
