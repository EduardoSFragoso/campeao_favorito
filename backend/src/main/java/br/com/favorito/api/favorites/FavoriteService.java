package br.com.favorito.api.favorites;

import br.com.favorito.api.auth.CurrentUserService;
import br.com.favorito.api.common.DuplicateResourceException;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteService {

  private final FavoriteRepository favorites;
  private final CurrentUserService currentUser;

  public List<Favorite> listMine() {
    return favorites.findByUserIdOrderByCreatedAtDesc(currentUser.userId());
  }

  @Transactional
  public Favorite add(FavoriteDtos.FavoriteRequest req) {
    Long userId = currentUser.userId();
    if (favorites.existsByUserIdAndChampionIdIgnoreCase(userId, req.championId())) {
      throw new DuplicateResourceException("Campeão já está nos favoritos.");
    }
    Favorite f = new Favorite();
    f.setUserId(userId);
    f.setChampionId(req.championId());
    f.setChampionName(req.championName());
    f.setTitle(req.title());
    return favorites.save(f);
  }

  @Transactional
  public void remove(String championId) {
    Favorite f =
        favorites
            .findByUserIdAndChampionIdIgnoreCase(currentUser.userId(), championId)
            .orElseThrow(() -> new NoSuchElementException("Favorito não encontrado."));
    favorites.delete(f);
  }
}
