package br.com.favorito.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do OpenAPI/Swagger UI (http://localhost:8080/swagger-ui.html). O botão Authorize aceita
 * o JWT devolvido por /api/auth/login.
 */
@Configuration
public class OpenApiConfig {

  public static final String JWT_SCHEME = "BearerAuth";

  @Bean
  OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Campeões Favoritos LOL — API")
                .description(
                    """
                    API do projeto campeão_favorito: favoritos de campeões salvos no \
                    Neon Postgres e integração com a Riot API (invocador, elo, maestria \
                    e rotação semanal). Campeões e splasharts vêm do Data Dragon (CDN \
                    estática, sem chave).\
                    """)
                .version("0.1.0"))
        .components(
            new Components()
                .addSecuritySchemes(
                    JWT_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME));
  }
}
