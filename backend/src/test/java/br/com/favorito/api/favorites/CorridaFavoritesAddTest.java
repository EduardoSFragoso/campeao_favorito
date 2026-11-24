package br.com.favorito.api.favorites;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.favorito.api.auth.CurrentUserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Corrida no favorites.add: entre o existsByUserIdAndChampionIdIgnoreCase() e o save() outra
 * requisição do mesmo usuário pode inserir o mesmo campeão. O código não resolve a corrida — é o
 * UNIQUE (user_id, champion_id) do banco que garante 1 linha; quem perde toma a violação,
 * convertida em {@link br.com.favorito.api.common.DuplicateResourceException} (409 no HTTP) — antes
 * da correção a violação escapava crua e virava 500 no catch-all.
 */
class CorridaFavoritesAddTest {

  @Test
  void quemPerdeACorridaNaoDuplicaOFavorito() {
    FavoriteRepository favorites = mock(FavoriteRepository.class);
    CurrentUserService currentUser = mock(CurrentUserService.class);

    when(currentUser.userId()).thenReturn(7L);
    // janela da corrida: o exists foi visto ANTES do insert do outro thread
    when(favorites.existsByUserIdAndChampionIdIgnoreCase(7L, "ahri")).thenReturn(false);
    Favorite salvo = new Favorite();
    salvo.setId(1L);
    salvo.setUserId(7L);
    salvo.setChampionId("ahri");
    // stub duplo: a implementação antiga chamava save(); a nova saveAndFlush() (para a violação
    // de unicidade acontecer DENTRO do método, no escopo do catch)
    when(favorites.save(any(Favorite.class)))
        .thenReturn(salvo) // 1ª thread vence a corrida
        .thenThrow(new DataIntegrityViolationException("uk_user_champion")); // 2ª bate no UNIQUE
    when(favorites.saveAndFlush(any(Favorite.class)))
        .thenReturn(salvo)
        .thenThrow(new DataIntegrityViolationException("uk_user_champion"));

    FavoriteService service = new FavoriteService(favorites, currentUser);
    var req = new FavoriteDtos.FavoriteRequest("ahri", "Ahri", "A Nove-Caudas");

    Assertions.assertDoesNotThrow(() -> service.add(req));
    Assertions.assertThrows(
        br.com.favorito.api.common.DuplicateResourceException.class, () -> service.add(req));

    verify(favorites, times(2)).saveAndFlush(any(Favorite.class));
  }
}
