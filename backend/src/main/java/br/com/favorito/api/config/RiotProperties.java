package br.com.favorito.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "riot.api")
public record RiotProperties(String key, String defaultPlatform) {}
