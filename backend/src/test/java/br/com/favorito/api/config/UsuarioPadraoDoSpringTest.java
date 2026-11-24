package br.com.favorito.api.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.favorito.api.ApiApplication;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * A API autentica SÓ via JWT (JwtAuthFilter); nunca houve login por formulário. Sem
 * UserDetailsService no contexto, o Spring Security gera um usuário/senha padrão por request e LOGA
 * a senha ("Using generated security password") — ruído que ainda sugere uma superfície de login
 * que não existe. O {@link UserDetailsServiceAutoConfiguration} é quem gera isso; aqui travamos que
 * ele fique excluído do {@code @SpringBootApplication}.
 */
class UsuarioPadraoDoSpringTest {

  @Test
  @DisplayName("ApiApplication exclui a UserDetailsServiceAutoConfiguration do Boot")
  void excluiAutoconfigDoUsuarioPadrao() {
    SpringBootApplication anotacao =
        ApiApplication.class.getAnnotation(SpringBootApplication.class);
    assertNotNull(anotacao, "ApiApplication precisa declarar @SpringBootApplication");
    assertTrue(
        Arrays.asList(anotacao.exclude()).contains(UserDetailsServiceAutoConfiguration.class),
        "Inclua exclude = UserDetailsServiceAutoConfiguration.class no @SpringBootApplication"
            + " — sem isso o Boot gera usuário/senha padrão no log a cada start");
  }
}
