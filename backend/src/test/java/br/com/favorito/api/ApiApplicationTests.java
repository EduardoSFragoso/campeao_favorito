package br.com.favorito.api;

import br.com.favorito.api.config.SegredoDeTeste;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test do contexto inteiro, com o esquema migrado do zero pelo Flyway. Exige um Postgres real
 * — no CI roda contra o serviço efêmero do job backend; localmente, só quando DB_URL estiver no
 * ambiente (senão fica fora e o build não quebra). Define JWT_SECRET própria: sem perfil ativo, o
 * guard do JwtService recusa o placeholder do application.yml (verdadeira a regra, também aqui).
 */
@SpringBootTest(properties = "app.jwt.secret=" + SegredoDeTeste.JWT_SECRET)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ApiApplicationTests {

  @Test
  void contextLoads() {}
}
