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
 * Caracterização da corrida no favorites.add: entre o existsByUserIdAndChampionIdIgnoreCase() e o
 * save() outra requisição do mesmo usuário pode inserir o mesmo campeão. O código não resolve a
 * corrida — é o UNIQUE (user_id, champion_id) do banco que garante 1 linha; quem perde toma a
 * exceção de integridade SEM tratamento (hoje vira 500 no HTTP via catch-all).
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
    when(favorites.save(any(Favorite.class)))
        .thenReturn(salvo) // 1ª thread vence a corrida
        .thenThrow(new DataIntegrityViolationException("uk_user_champion")); // 2ª bate no UNIQUE

    FavoriteService service = new FavoriteService(favorites, currentUser);
    var req = new FavoriteDtos.FavoriteRequest("ahri", "Ahri", "A Nove-Caudas");

    Assertions.assertDoesNotThrow(() -> service.add(req));
    Assertions.assertThrows(DataIntegrityViolationException.class, () -> service.add(req));

    verify(favorites, times(2)).save(any(Favorite.class));
  }
}
