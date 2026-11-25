package br.com.favorito.api.common;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.favorito.api.auth.JwtAuthFilter;
import br.com.favorito.api.auth.JwtService;
import br.com.favorito.api.config.SecurityConfig;
import br.com.favorito.api.config.TestPropertiesConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato de mensagens de erro: validação NOSSA (EntradaInvalidaException) responde 400 com a
 * mensagem acionável; IllegalArgumentException solta e 404 de rota inexistente NÃO ecoam detalhe
 * para o client — vão para o log, a resposta é genérica (mesmo padrão do "Erro interno."). Montagem
 * de segurança igual ao FavoriteControllerTest (cadeia real, JwtService mockado).
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProvoController.class)
@Import({
  SecurityConfig.class,
  JwtAuthFilter.class,
  TestPropertiesConfig.class,
  GlobalExceptionHandlerTest.ProvoController.class
})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:4200")
class GlobalExceptionHandlerTest {

  @Autowired MockMvc mvc;

  // dependência do JwtAuthFilter real
  @MockitoBean JwtService jwtService;

  /** Controller-fantasma que provoca a exceção — não existe no app real. */
  @RestController
  static class ProvoController {

    @GetMapping("/teste/argumento")
    public String argumento() {
      throw new IllegalArgumentException("Plataforma inválida: detalhe interno #42");
    }

    @GetMapping("/teste/entrada-invalida")
    public String entradaInvalida() {
      throw new EntradaInvalidaException("Plataforma inválida: xx1");
    }
  }

  @Test
  void argumentoIlegal400ComMensagemGenerica() throws Exception {
    mvc.perform(get("/teste/argumento").with(user("7")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Requisição inválida."));
  }

  /** Validação NOSSA (EntradaInvalidaException): 400 com a mensagem acionável inteira. */
  @Test
  void entradaInvalida400ComMensagemEspecifica() throws Exception {
    mvc.perform(get("/teste/entrada-invalida").with(user("7")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Plataforma inválida: xx1"));
  }

  /** 404 real: rota sem controller e sem recurso estático → NoResourceFoundException. */
  @Test
  void rotaInexistente404SemEcoarOCaminho() throws Exception {
    mvc.perform(get("/teste/caminho/interno/secreto").with(user("7")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Rota não encontrada."));
  }
}
