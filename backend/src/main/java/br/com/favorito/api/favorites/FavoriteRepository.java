package br.com.favorito.api.favorites;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

  List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

  Optional<Favorite> findByUserIdAndChampionIdIgnoreCase(Long userId, String championId);

  boolean existsByUserIdAndChampionIdIgnoreCase(Long userId, String championId);

  void deleteByUserIdAndChampionIdIgnoreCase(Long userId, String championId);
}
