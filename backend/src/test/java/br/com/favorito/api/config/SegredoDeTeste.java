package br.com.favorito.api.config;

/**
 * Segredo FALSO para os {@code @SpringBootTest} de banco real (smoke e corrida), que sobem o
 * contexto inteiro sem perfil: o guard do {@code JwtService} recusa o placeholder do {@code
 * application.yml} fora de dev/test, então esses testes precisam de uma chave própria. Nunca
 * autentica nada real e nunca deve virar segredo de verdade.
 */
public final class SegredoDeTeste {

  public static final String JWT_SECRET =
      "chave-de-teste-do-smoke-nao-producao-0123456789abcdefghij";

  private SegredoDeTeste() {}
}
