package br.com.favorito.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test do contexto inteiro, com o esquema migrado do zero pelo Flyway. Exige um Postgres real
 * — no CI roda contra o serviço efêmero do job backend; localmente, só quando DB_URL estiver no
 * ambiente (senão fica fora e o build não quebra).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ApiApplicationTests {

  @Test
  void contextLoads() {}
}
