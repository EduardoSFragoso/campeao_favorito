package br.com.favorito.api.favorites;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FavoriteDtos() {
  public record FavoriteRequest(
      @NotBlank @Size(max = 64) String championId,
      @NotBlank @Size(max = 64) String championName,
      @Size(max = 256) String title) {}
}
