package br.com.favorito.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.favorito.api.auth.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Caracterização do {@code getRemoteAddr()} atrás do nginx. Em produção, {@code
 * server.forward-headers-strategy: framework} (application-prod.yml) faz o servidor registrar o
 * ForwardedHeaderFilter, que reescreve o {@code getRemoteAddr()} para o IP informado pelo proxy — é
 * esse IP que o LoginRateLimiter usa como bucket. Aqui o filtro REAL do spring-web é registrado
 * explicitamente (o slice @WebMvcTest não o autoconfigura, em servidor real quem o registra é o
 * WebMvcAutoConfiguration) e uma controladora-sonda devolve o remoteAddr que o controller enxergou.
 *
 * <p>Comportamento atual fixado: vale o PRIMEIRO IP da lista X-Forwarded-For e o cliente é confiado
 * irrestritamente (a strategy framework confia nos headers recebidos). Se alguém mudar a strategy,
 * trocar de filtro ou começar a validar a cadeia, estes testes falham de propósito.
 */
@WebMvcTest(SondaRemoteAddr.class)
@Import(ForwardedHeaderRemoteAddrTest.ConfigDeProducao.class)
@TestPropertySource(properties = "server.forward-headers-strategy=framework")
class ForwardedHeaderRemoteAddrTest {

  /** Espelho do que a strategy framework registra no servidor de produção. */
  @TestConfiguration
  static class ConfigDeProducao {
    @Bean
    ForwardedHeaderFilter forwardedHeaderFilter() {
      return new ForwardedHeaderFilter();
    }
  }

  @Autowired MockMvc mvc;

  // o JwtAuthFilter real entra no slice por ser bean de Filter; o mock só satisfaz o construtor
  @MockitoBean JwtService jwtService;

  @MockitoBean
  br.com.favorito.api.auth.UserRepository users; // construtor do JwtAuthFilter (token_ver)

  @Test
  void ipDoXForwardedForViraORemoteAddrVistoPeloController() throws Exception {
    mvc.perform(get("/probe/remote-addr").header("X-Forwarded-For", "198.51.100.7"))
        .andExpect(status().isOk())
        .andExpect(content().string("198.51.100.7"));
  }

  @Test
  void primeiroIpDaCadeiaDeProxiesEUsoComoRemoteAddr() throws Exception {
    mvc.perform(
            get("/probe/remote-addr").header("X-Forwarded-For", "198.51.100.7, 10.0.0.1, 10.0.0.2"))
        .andExpect(status().isOk())
        .andExpect(content().string("198.51.100.7"));
  }

  @Test
  void headerForwardedPadraoRFC7239TambemSobrescreveORemoteAddr() throws Exception {
    mvc.perform(get("/probe/remote-addr").header("Forwarded", "for=198.51.100.9"))
        .andExpect(status().isOk())
        .andExpect(content().string("198.51.100.9"));
  }

  @Test
  void semHeaderDeProxyORemoteAddrEhOLoopbackDoMockMvc() throws Exception {
    // MockHttpServletRequest nasce com 127.0.0.1 — em prod esse é o bucket dos
    // clientes que chegam SEM X-Forwarded-For (ver LoginRateLimiterForwardedTest).
    mvc.perform(get("/probe/remote-addr"))
        .andExpect(status().isOk())
        .andExpect(content().string("127.0.0.1"));
  }
}

/** Sonda exclusiva de teste: devolve o que o controller enxerga em getRemoteAddr(). */
@RestController
class SondaRemoteAddr {

  @GetMapping("/probe/remote-addr")
  String remoteAddr(HttpServletRequest request) {
    return request.getRemoteAddr();
  }
}
