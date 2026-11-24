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
 * convertida em {@link br.com.favorito.api.common.DuplicateResourceException} (409 no HTTP) SÓ
 * quando a restrição violada é uk_user_champion (lido na causa); outra violação (ex.: a FK
 * fk_favorites_user) segue como erro de servidor.
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
        .thenThrow(br.com.favorito.api.support.ViolacaoDeBanco.em("uk_user_champion"));
    when(favorites.saveAndFlush(any(Favorite.class)))
        .thenReturn(salvo)
        .thenThrow(br.com.favorito.api.support.ViolacaoDeBanco.em("uk_user_champion"));

    FavoriteService service = new FavoriteService(favorites, currentUser);
    var req = new FavoriteDtos.FavoriteRequest("ahri", "Ahri", "A Nove-Caudas");

    Assertions.assertDoesNotThrow(() -> service.add(req));
    Assertions.assertThrows(
        br.com.favorito.api.common.DuplicateResourceException.class, () -> service.add(req));

    verify(favorites, times(2)).saveAndFlush(any(Favorite.class));
  }

  @Test
  void violacaoDeOutraRestricaoMantemErroDeServidor() {
    // a FK fk_favorites_user (user inexistente) NÃO é duplicata: sem essa distinção o cliente
    // receberia 409 "Campeão já está nos favoritos." para um bug de dados
    FavoriteRepository favorites = mock(FavoriteRepository.class);
    CurrentUserService currentUser = mock(CurrentUserService.class);

    when(currentUser.userId()).thenReturn(7L);
    when(favorites.existsByUserIdAndChampionIdIgnoreCase(7L, "ahri")).thenReturn(false);
    when(favorites.saveAndFlush(any(Favorite.class)))
        .thenThrow(br.com.favorito.api.support.ViolacaoDeBanco.em("fk_favorites_user"));

    FavoriteService service = new FavoriteService(favorites, currentUser);
    var req = new FavoriteDtos.FavoriteRequest("ahri", "Ahri", "A Nove-Caudas");

    Assertions.assertThrows(DataIntegrityViolationException.class, () -> service.add(req));
  }
}
