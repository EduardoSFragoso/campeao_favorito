package br.com.favorito.api.favorites;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "favorites",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_user_champion",
            columnNames = {"user_id", "champion_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Favorite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  /** Id do campeão no Data Dragon (ex.: "Aatrox", "Ahri"). */
  @Column(name = "champion_id", nullable = false, length = 64)
  private String championId;

  /** Cópia quando a submissão foi feita (o nome, para renderizar mesmo se a CDN mudar). */
  @Column(nullable = false, length = 64)
  private String championName;

  @Column(nullable = true, length = 256)
  private String title;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
