# handoff.md — campeão_favorito

Ponto de partida para quem continuar o projeto. Complementa o `README.md` (config/uso) e o
`docs/auditoria-infra.md` (checklist de infra). Regras de desenvolvimento: `AGENTS.md`.

## Estrutura

```
frontend/                Angular (GitHub Pages, CI publica dist/)
  src/app/core/          services (ApiUrl, Backend, Auth, DataDragon, http interceptor)
  src/app/pages/         home/grid, champion-detail, summoner, login, register, favorites
backend/                 Spring Boot 4 (Java 25), systemd na VM Oracle
  src/main/java/br/com/favorito/api/
    auth/                User, JwtService/JwtAuthFilter, AuthService, LoginRateLimiter
    favorites/           Favorite, controller/service/repository
    riot/                RiotController/Service/Client, DTOs
    common/              GlobalExceptionHandler, exceções, HealthController
    config/              SecurityConfig, CorsProperties, RiotProperties, OpenApiConfig
  src/main/resources/
    application.yml      defaults dev; prod sobrepõe via application-prod.yml
    db/migration/        Flyway (V1+: FONTE DA VERDADE do esquema)
docs/                    fase VM/TLS, chave de Riot pessoal, auditoria de infra
.github/workflows/       ci.yml, deploy-gh-pages.yml, monitor.yml (.github/dependabot.yml)
```

## Comandos

```bash
# Backend — build com TODOS os testes (CI roda exatamente isto)
cd backend && ./mvnw -B spotless:apply && ./mvnw -B verify

# Backend — jar para deploy (depois do verify)
cd backend && ./mvnw -B package -DskipTests

# Frontend
cd frontend && npx prettier --write . && npm run lint && npx ng test --watch=false && npx ng build

# Deploy backend na VM (eden-server) — jar via SFTP para /tmp primeiro
sudo mv /tmp/api-0.0.1-SNAPSHOT.jar /opt/campeao-favorito/app.jar
sudo systemctl restart campeao-favorito
sudo journalctl -u campeao-favorito -n 20 --no-pager
```

## Contratos que não podem quebrar

- **Autorização**: tudo atrás de JWT exceto `/api/auth/**`, `/api/health`, `/error` e os endpoints
  do Swagger (desligados no perfil prod). Novas rotas nasce protegidas por padrão.
- **Respostas de erro**: sempre `{"error": "..."}` do `GlobalExceptionHandler`. Exceções de
  negócio com `@ResponseStatus` **precisam** de handler específico no advice — o
  `@ExceptionHandler(Exception.class)` genérico captura antes e viraria 500 (já mordeu 401/409/429).
- **Front**: o nome de invocador vem como `gameName` (a Riot deprecou `name`). `League-V4`
  responde 403 com chave dev/pessoal — favoritos/perfil degradam sem elo, não podem falhar.
- **gameName/tagLine**: validados com regex unicode-safe antes de virar parâmetro de rota da Riot
  (`platform` tem whitelist — entra na construção do host, risco de SSRF).
- **Login**: rate limit 10 tentativas/min por IP (IP real via `X-Forwarded-For` do nginx).
- **Esquema do banco**: só por migração Flyway versionada. Nunca mexer em tabela na mão nem
  religar `ddl-auto` para algo diferente de `validate`.
- **Role da aplicação no Neon**: produção roda com `campeao_app` (senha forte no env da VM), com
  CRUD nas tabelas + `CREATE` apenas no schema `public` (o que o Flyway precisa para a próxima
  migration). Nada de `neondb_owner` no runtime. Conexão pelo host `-pooler` com `sslmode=require`
  (sem `channel_binding` — o JDBC não usa).
- **Projeto Neon**: produção vive em UM projeto; o segundo (legado) foi esvaziado. Confirme sempre
  que está no projeto certo antes de rodar qualquer `DROP`/`ALTER` — tabelas de outros apps podem
  morar junto (já mordido: `DROP` de tabela de terceiro recuperado via point-in-time restore).
- **Front chamando a API**: por HTTPS, origem servida em `config.json` gerado na CI
  (variável de repositório `API_URL`).
- **Cromas (Community Dragon)**: o frontend busca
  `https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champions/{key}.json`
  (key numérico do campeão; CDN estático, sem chave de API). Todas as cromas de uma skin
  compartilham a MESMA imagem de preview — o que diferencia cada croma é o par de hex em
  `colors` (renderizado como amostra/gradiente CSS). CDN fora → a página degrada sem amostras,
  sem erro visível. O card da skin (modal) mostra o splash do Data Dragon, que já é a
  resolução máxima disponível.

## Workflow de Git

- Branch protegida por ruleset `main-protection` (enforcement ativo): PR obrigatório, os dois jobs
  da CI como checks obrigatórios, linear history, força-push bloqueado, merge apenas
  **Rebase and merge** (cada commit entra com hash próprio; squash desligado).
- Mensagens: `tipo(escopo): assunto — detalhe` com assunto até ~70 caracteres.
- Segredos nunca versionados: dev usa `backend/.env` (gitignored), produção usa o
  `EnvironmentFile=/etc/campeao-favorito.env` do systemd na VM (manutenção manual, permissão 600).

## Armadilhas conhecidas (já morderam)

- **Spring Boot 4**: `@WebMvcTest` veio para `org.springframework.boot.webmvc.test.autoconfigure`
  (artifact `spring-boot-webmvc-test`) e o autoconfig do Flyway só liga com
  `spring-boot-starter-flyway` (com `flyway-core` puro a CI falhou com `missing table`).
- `@WithMockUser` não atravessa a cadeia de filtros real — nos `@WebMvcTest` de sécurité, use
  `SecurityMockMvcRequestPostProcessors.user("7")`.
- O mtime do jar buildado no Windows sai `1980-01-31` — validar conteúdo com
  `unzip -p target/...jar BOOT-INF/classes/... | grep`, nunca pelo timestamp.
- Conta "#" sem tag correta pode ser registro filtrado pela Riot (ex.: `hideonbush#KR`);
  ouro absoluto só olhando a conta com a tag certa (`Hide on bush#KR1`).
- Restart da VM derruba a sessão SSH — reabrir terminal.
- CRLF local no frontend quebra o `prettier --check`; a CI é a verdade.

## Pendências / próximos passos

- **Auditoria de infra concluída em 2026-11-23** — `docs/auditoria-infra.md` com todas as seções
  verificadas: Neon (role mínima + Flyway em produção), VM (portas, env 600, SSH só chave,
  rpcbind desativado, Security List validada externamente), CORS (origem estranha 403 sem ACAO,
  origem do Pages 200 com ACAO exata), Dependabot, CI com Postgres efêmero, ruleset de proteção.
  `gitleaks git --redact` rodado localmente após o falso positivo do `JwtServiceTest`:
  **no leaks found** (allowlist cirúrgica em `.gitleaks.toml`).
- Com chave de produção pessoal da Riot, o elo ranqueado volta a ser exibido (o degrade
  já está pronto).
