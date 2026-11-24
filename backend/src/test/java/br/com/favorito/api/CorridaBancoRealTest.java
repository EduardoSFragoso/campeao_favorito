package br.com.favorito.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.favorito.api.auth.AuthDtos;
import br.com.favorito.api.auth.AuthService;
import br.com.favorito.api.auth.UserRepository;
import br.com.favorito.api.config.SegredoDeTeste;
import br.com.favorito.api.favorites.FavoriteDtos;
import br.com.favorito.api.favorites.FavoriteRepository;
import br.com.favorito.api.favorites.FavoriteService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Rede de segurança da corrida em register e favorites.add DIRETO NA TABELA (check-then-insert não
 * resolve a corrida — é o UNIQUE de users.email e de favorites(user_id, champion_id) que protege).
 * Exige Postgres real, como o smoke test: roda na CI (job backend) e localmente só com DB_URL no
 * ambiente. Duas threads correm em paralelo; o invariante é 1 linha, nunca duplicata.
 */
@SpringBootTest(properties = "app.jwt.secret=" + SegredoDeTeste.JWT_SECRET)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CorridaBancoRealTest {

  @Autowired AuthService authService;
  @Autowired FavoriteService favoriteService;
  @Autowired UserRepository users;
  @Autowired FavoriteRepository favorites;

  /**
   * Roda duas tarefas em paralelo (cerco de partida); devolve [resultadoA, resultadoB], em que um
   * resultado é o valor OU o Throwable da tarefa.
   */
  private static List<Object> executar(Callable<?> a, Callable<?> b) throws Exception {
    CountDownLatch largada = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Object>> futuros = new ArrayList<>();
      for (Callable<?> t : List.of(a, b)) {
        futuros.add(
            pool.submit(() -> {
              largada.await();
              try {
                return (Object) t.call();
              } catch (Throwable e) {
                return (Object) e;
              }
            }));
      }
      largada.countDown();
      List<Object> resultados = new ArrayList<>();
      resultados.add(futuros.get(0).get(30, TimeUnit.SECONDS));
      resultados.add(futuros.get(1).get(30, TimeUnit.SECONDS));
      return resultados;
    } finally {
      pool.shutdownNow();
    }
  }

  private static boolean venceu(Object resultado) {
    return !(resultado instanceof Throwable);
  }

  private void limparUsuarios(List<String> emails) {
    for (String email : emails) {
      users
          .findByEmailIgnoreCase(email)
          .ifPresent(u -> {
            favorites.deleteAll(favorites.findByUserIdOrderByCreatedAtDesc(u.getId()));
            users.delete(u);
          });
    }
  }

  @Test
  void registerConcorrenteSoCriaUmUsuario() throws Exception {
    limparUsuarios(List.of("corrida-register@exemplo.dev"));
    String email = "corrida-register@exemplo.dev";

    List<Object> resultados =
        executar(
            () ->
                authService.register(new AuthDtos.RegisterRequest(email, "senha-forte-123", "Dup")),
            () ->
                authService.register(
                    new AuthDtos.RegisterRequest(email, "senha-forte-123", "Dup")));

    long criados = resultados.stream().filter(CorridaBancoRealTest::venceu).count();
    long falhados = resultados.size() - criados;
    assertEquals(1, criados, "só um register pode vencer: " + resultados);
    assertEquals(1, falhados, "o outro register tem de falhar: " + resultados);
    assertEquals(1, users.findByEmailIgnoreCase(email).stream().count(), "UNIQUE de users.email");

    limparUsuarios(List.of(email));
  }

  @Test
  void favoritosConcorrentesSoCriamUm() throws Exception {
    String email = "corrida-favorito@exemplo.dev";
    limparUsuarios(List.of(email));
    authService.register(new AuthDtos.RegisterRequest(email, "senha-forte-123", "Dup"));
    Long userId = users.findByEmailIgnoreCase(email).orElseThrow().getId();

    var req = new FavoriteDtos.FavoriteRequest("ahri", "Ahri", "A Nove-Caudas");
    List<Object> resultados =
        executar(() -> addAutenticado(userId, req), () -> addAutenticado(userId, req));

    long criados = resultados.stream().filter(CorridaBancoRealTest::venceu).count();
    assertTrue(criados >= 1, "ao menos um add vence: " + resultados);
    assertEquals(
        1,
        favorites.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .filter(f -> "ahri".equalsIgnoreCase(f.getChampionId()))
            .count(),
        "UNIQUE (user_id, champion_id) — resta 1 linha: " + resultados);

    limparUsuarios(List.of(email));
  }

  private Object addAutenticado(Long userId, FavoriteDtos.FavoriteRequest req) {
    try {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  String.valueOf(userId), null, AuthorityUtils.NO_AUTHORITIES));
      return favoriteService.add(req);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
