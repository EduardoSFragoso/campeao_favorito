package br.com.favorito.api.favorites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.favorito.api.auth.CurrentUserService;
import br.com.favorito.api.common.DuplicateResourceException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Regras dos favoritos: duplicata, cópia dos dados na criação e remoção com dono certo. */
class FavoriteServiceTest {

  private static final Long USER = 7L;

  private FavoriteRepository repo;
  private CurrentUserService currentUser;
  private FavoriteService service;

  @BeforeEach
  void setUp() {
    repo = mock(FavoriteRepository.class);
    currentUser = mock(CurrentUserService.class);
    when(currentUser.userId()).thenReturn(USER);
    service = new FavoriteService(repo, currentUser);
  }

  private FavoriteDtos.FavoriteRequest req() {
    return new FavoriteDtos.FavoriteRequest("Ahri", "Ahri", "a nine-tailed fox");
  }

  @Test
  void listaSomenteDoUsuarioLogado() {
    List<Favorite> esperado = List.of(new Favorite());
    when(repo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(esperado);

    assertEquals(esperado, service.listMine());
    verify(repo).findByUserIdOrderByCreatedAtDesc(USER);
  }

  @Test
  void addCopiaCamposDaRequisicao() {
    when(repo.existsByUserIdAndChampionIdIgnoreCase(USER, "Ahri")).thenReturn(false);
    when(repo.saveAndFlush(any(Favorite.class))).thenAnswer(inv -> inv.getArgument(0));

    Favorite salvo = service.add(req());

    ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
    verify(repo).saveAndFlush(captor.capture());
    assertEquals(USER, captor.getValue().getUserId());
    assertEquals("Ahri", salvo.getChampionId());
    assertEquals("Ahri", salvo.getChampionName());
    assertEquals("a nine-tailed fox", salvo.getTitle());
  }

  @Test
  void addDuplicadoRejeitaAntesDeSalvar() {
    when(repo.existsByUserIdAndChampionIdIgnoreCase(USER, "Ahri")).thenReturn(true);

    assertThrows(DuplicateResourceException.class, () -> service.add(req()));
    verify(repo, never()).save(any());
  }

  @Test
  void addQueBateNoUniqueEmCorridaVira409() {
    // exists passou, mas o INSERT bate no UNIQUE (corrida) — a violação vira 409, nunca 500
    when(repo.existsByUserIdAndChampionIdIgnoreCase(USER, "Ahri")).thenReturn(false);
    when(repo.saveAndFlush(any(Favorite.class)))
        .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_user_champion"));

    assertThrows(DuplicateResourceException.class, () -> service.add(req()));
  }

  @Test
  void removeDeletaOFavoritoDoUsuario() {
    Favorite f = new Favorite();
    when(repo.findByUserIdAndChampionIdIgnoreCase(USER, "Ahri")).thenReturn(Optional.of(f));

    service.remove("Ahri");

    verify(repo).delete(f);
  }

  @Test
  void removeInexistenteViraNotFound() {
    when(repo.findByUserIdAndChampionIdIgnoreCase(USER, "Ahri")).thenReturn(Optional.empty());

    assertThrows(NoSuchElementException.class, () -> service.remove("Ahri"));
    verify(repo, never()).delete(any());
  }

  @Test
  void removeDeOutroUsuarioNaoAcha() {
    // championId existe, mas pertence a outro usuário — o finder com userId não retorna
    when(repo.findByUserIdAndChampionIdIgnoreCase(USER, "Alheio")).thenReturn(Optional.empty());

    assertThrows(NoSuchElementException.class, () -> service.remove("Alheio"));
    verify(repo, never()).delete(any());
  }
}
