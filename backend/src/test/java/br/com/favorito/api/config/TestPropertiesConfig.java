package br.com.favorito.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra os records de propriedades que o slice @WebMvcTest não escaneia (a anotação
 * &#064;ConfigurationProperties não é um &#064;Component), mas de que os controllers e a
 * SecurityConfig dependem.
 */
@Configuration
@EnableConfigurationProperties({CorsProperties.class, RiotProperties.class})
public class TestPropertiesConfig {}
