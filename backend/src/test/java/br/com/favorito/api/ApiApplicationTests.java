package br.com.favorito.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test do contexto inteiro. Exige um banco real (Neon ou Postgres local) — por isso só roda
 * quando DB_URL está no ambiente; no CI (sem banco) fica fora e o build não quebra.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ApiApplicationTests {

  @Test
  void contextLoads() {}
}
