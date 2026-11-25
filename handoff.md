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
docs/                    fase VM/TLS, chave de Riot pessoal, rotação de chaves, auditoria de infra
.github/workflows/       ci.yml, deploy-gh-pages.yml, monitor.yml, backup-neon.yml (.github/dependabot.yml)
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
# o jar anterior é preservado em app.jar.bak para rollback imediato
sudo mv /opt/campeao-favorito/app.jar /opt/campeao-favorito/app.jar.bak
sudo mv /tmp/api-0.0.1-SNAPSHOT.jar /opt/campeao-favorito/app.jar
sudo systemctl restart campeao-favorito
sudo journalctl -u campeao-favorito -n 20 --no-pager

# Rollback — devolver o jar imediatamente anterior e reiniciar
sudo mv /opt/campeao-favorito/app.jar.bak /opt/campeao-favorito/app.jar
sudo systemctl restart campeao-favorito
```

## Contratos que não podem quebrar

- **Autorização**: tudo atrás de JWT exceto `/api/auth/**`, `/api/health`, `/error` e os endpoints
  do Swagger (desligados no perfil prod). Novas rotas nasce protegidas por padrão.
- **Guard do JWT_SECRET** (`JwtService`): o placeholder do `application.yml` é **recusado em
  qualquer perfil**, exceto quando o perfil `dev` ou `test` está ativo de forma explícita (aí
  vale warning e segue, para sprint local). Segredo com < 32 bytes é recusado em **todos** os
  perfis (o HS256 não é seguro — o próprio io.jsonwebtoken rejeitaria; a regra fecha antes com
  mensagem acionável). Os dois `@SpringBootTest` de banco real (o smoke `ApiApplicationTests`
  e o `CorridaBancoRealTest`) definem sua própria `app.jwt.secret` via `SegredoDeTeste` — sem
  isso o boot deles falharia sem perfil. Testes de slice usam `@MockitoBean JwtService`, então
  não passam pelo guard.
- **Respostas de erro**: sempre `{"error": "..."}` do `GlobalExceptionHandler`. Exceções de
  negócio com `@ResponseStatus` **precisam** de handler específico no advice — o
  `@ExceptionHandler(Exception.class)` genérico captura antes e viraria 500 (já mordeu 401/409/429).
- **Front**: o nome de invocador vem como `gameName` (a Riot deprecou `name`). `League-V4`
  responde 403 com chave dev/pessoal — favoritos/perfil degradam sem elo, não podem falhar.
- **gameName/tagLine**: validados com regex unicode-safe antes de virar parâmetro de rota da Riot
  (`platform` tem whitelist — entra na construção do host, risco de SSRF).
- **Login**: rate limit 10 tentativas/min por IP. O "IP real" existe porque o nginx
  **sobrescreve** `X-Forwarded-For` com `$remote_addr` e **zera** o header `Forwarded` — o
  `ForwardedHeaderFilter` do Spring (`strategy: framework`) confia no primeiro IP desses
  headers (comprovado pelos testes do PR #58); antes da correção os headers iam do cliente
  e o limite era burlável. Só é seguro enquanto o nginx for o **único** proxy na frente da
  API: com CDN ou outro proxy, revisar o vhost e a strategy. O vhost ativo é a **cópia**
  em `/etc/nginx/sites-enabled/` (não é link para `sites-available`); o
  `/etc/nginx/proxy_params` tem `$proxy_add_x_forwarded_for`, mas não é incluído por este
  vhost. O trecho efetivo do vhost (sem host real — ver `docs/fase4-oracle-vm.md`):

  ```nginx
  proxy_pass http://127.0.0.1:8080;
  proxy_set_header Host $host;
  proxy_set_header X-Real-IP $remote_addr;
  proxy_set_header X-Forwarded-For $remote_addr;
  proxy_set_header Forwarded "";
  proxy_set_header X-Forwarded-Proto $scheme;
  ```
- **Rate limits (janela fixa por chave, em memória — motor único `RateLimiterDeJanela`)**:
  login 10/min por IP; register 5/min por IP; `/api/riot/**` 10/min POR USUÁRIO (id do token)
  MAIS um limite GLOBAL de 40/min para todas as rotas Riot juntas (chave fixa no mesmo motor;
  o por-usuário é checado primeiro, então quem já estourou o próprio não consome vaga do
  global). Teto de chaves nos mapas (10.000: vencidas saem primeiro, depois as mais antigas).
  Nuances: o IP real vale o aviso do item acima (nginx único proxy); os limites de
  login/register SECUEM a corrente do IP — se a strategy de forwarded headers mudar, os três
  viram um contador só do loopback; o da Riot depende do `Authentication.getName()` — filtros
  que trocarem o principal mudam a chave.
- **RiotService**: a platform é normalizada (trim + minúsculas) ANTES da chave do cache —
  "BR1", " br1 " e "br1" dividem a mesma entrada. O `gameName` exige ao menos um caractere
  alfanumérico (lookahead na mesma regex do controller — rejeita `..`, ` - `). Os evicts de
  mais-antigo nos mapas (cache, locks e `RateLimiterDeJanela`) são null-safe e travam se um
  dia o mapa aparecer inconsistente.

- **Esquema do banco**: só por migração Flyway versionada. Nunca mexer em tabela na mão nem
  religar `ddl-auto` para algo diferente de `validate`.
- **Roles no Neon**: produção tem duas roles — `campeao_app` (senha forte no env da VM), **dona
  das tabelas** (CRUD + `CREATE` apenas no schema `public`, o que o Flyway precisa para a próxima
  migration), usada pelo Flyway e pela aplicação; e `campeao_backup`, **só leitura**, usada pelo
  backup (`backup-neon.yml`). Nada de `neondb_owner` no runtime. Conexão pelo host `-pooler` com
  `sslmode=require` (sem `channel_binding` — o JDBC não usa).
- **Projeto Neon**: produção vive em UM projeto; o segundo (legado) foi esvaziado. O banco só
  tem `users`, `favorites` e `flyway_schema_history` (confirmado em 2026-11-24). Confirme sempre
  que está no projeto certo antes de rodar qualquer `DROP`/`ALTER` (lição antiga: um `DROP` de
  tabela de terceiro só foi recuperado via point-in-time restore).
- **Front chamando a API**: por HTTPS, origem servida em `config.json` gerado na CI
  (variável de repositório `API_URL`).
- **Maestria no grid**: `GET /api/riot/masteries` (JWT obrigatório) devolve a maestria COMPLETA
  por campeão (`/champion-masteries/by-puuid/{puuid}`, sem `/top`), cacheada por conta com TTL
  de 60s. O frontend guarda em sessionStorage o último invocador buscado
  (`SummonerStore` → primeira página "campeões" abre com badges ⭐). Falha da maestria nunca
  derruba o perfil — só some o badge. Todas as rotas `/api/riot/**` exigem JWT.
- **Cromas (Community Dragon)**: o frontend busca
  `https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champions/{key}.json`
  (key numérico do campeão; CDN estático, sem chave de API). Todas as cromas de uma skin
  compartilham a MESMA imagem de preview — o que diferencia cada croma é o par de hex em
  `colors` (renderizado como amostra/gradiente CSS). CDN fora → a página degrada sem amostras,
  sem erro visível. O card da skin (modal) mostra o splash do Data Dragon, que já é a
  resolução máxima disponível.

## Workflow de Git

- Branch protegida pelo ruleset `protege-main` (enforcement ativo; espelho versionado em
  `.github/rulesets/protege-main.json`): PR obrigatório, os dois jobs da CI como checks obrigatórios
  com branch atualizada antes do merge (strict), conversas de review resolvidas, força-push,
  deleção e merge commits bloqueados (`required_linear_history`), sem bypass (nem admin),
  **Required approvals = 0** e revisão de Codeowners **não exigida** — o portão do merge é a CI
  **mais a revisão manual do dono**; mudanças em `.github/workflows/` dependem dessa revisão
  manual (escopo no `CODEOWNERS`). Merge apenas **Rebase and merge** (só método habilitado).
  Anti-fake approval ligado (`require_extra_approval_for_unattributed_changes`).
- Checklist administrativo do repositório: `docs/configuracao-repositorio.md`.
- Mensagens: `tipo(escopo): assunto — detalhe` com assunto até ~70 caracteres.
- Segredos nunca versionados: dev usa `backend/.env` (gitignored), produção usa o
  `EnvironmentFile=/etc/campeao-favorito.env` do systemd na VM (manutenção manual, permissão 600).
- Backup do Neon: `backup-neon.yml` roda diário (+ _Run workflow_ manual), publica artifact `.gpg`
  (30 dias). Antes de criptografar o job valida o dump com `pg_restore --list` e falha se o TOC
  tiver menos de 3 entradas `TABLE DATA`; a chave do apt/pgdg vem por `curl -fsSL` com
  fingerprint conferida (e o cliente Postgres vem na imagem quando possível). Actions fixadas por
  SHA de commit. Secrets `NEON_DATABASE_URL` (conexão direta, sem `-pooler`) e `BACKUP_PASSPHRASE`
  no Actions — a passphrase só existe com o dono; sem ela o artifact é lixo. Restauração já
  validada em Postgres descartável (2026-11-24).

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

## Histórico de correções (PRs 52–70, 2026-11-24)

Cada linha em uma frase; branch/main sempre pelo fluxo PR → Rebase and merge.

| PR | O que corrigiu |
|---|---|
| #52 | Espelho do ruleset alinhado à API real e portão do merge corrigido (sem Codeowners, CI + revisão do dono). |
| #53 | Supressão de CSRF justificada e blocklist de schemes no teste de segurança. |
| #54 | Comentário da supressão do CodeQL movido para a linha do alerta. |
| #55 | Docs com o estado real de infra (roles do Neon, VM, backup). |
| #56 | Interceptor só anexa Bearer para URLs na base da API (nunca em hosts externos, ex. Community Dragon). |
| #57 | Rede de segurança: rate limit, rota protegida sem token, corridas em register/favorites, chaves de cache. |
| #58 | Caracteriza o `getRemoteAddr()` com o `ForwardedHeaderFilter` real (base da correção do rate limit). |
| #59 | Docs: nginx sobrescreve `X-Forwarded-For` com `$remote_addr` e zera `Forwarded`. |
| #60 | Guardas de entrada e teto de cache/locks no `RiotService`; interceptor usa `startsWith(base + '/')` (tolera `//host`). |
| #61 | Rate limit: register 5/min por IP; `/api/riot/**` 30/min por usuário. |
| #62 | Riot 10/min por usuário + limite global 40/min; platform normalizada; `gameName` exige alfanumérico; removes null-safe. |
| #63 | `JwtService` recusa o placeholder e segredo < 32 bytes em qualquer perfil (exceto dev/test explícitos). |
| #64 | 409 em duplicatas (register/favorites), rotation separada por platform e fim do usuário/senha padrão no log. |
| #65 | Backup valida TOC do dump (≥3 `TABLE DATA`), fingerprint pgdg conferida e action fixada por SHA. |
| #66 | Todas as actions restantes fixadas por SHA de commit, tag no comentário. |
| #67 | Frontend: 401/403 da API limpa sessão e leva ao login, sem laço e sem afetar login/register. |
| #68 | 409 só para a constraint esperada (`users_email_key`/`uk_user_champion`, lida na causa); outras violações seguem 500. |
| #69 | Colisão determinística no Postgres real prova os nomes das constraints na exceção registrada por #68. |
| #70 | DTOs da Riot toleram `null` numérico (AS_EMPTY) e erro de rede/leitura do `RiotClient` vira 502 genérico. |

## Pendências / próximos passos

Priorizadas — da mais urgente para a menos:

1. **Chave de produção pessoal da Riot** — com ela o elo ranqueado volta a ser exibido (o
   degrade já está pronto). Rascunho em `docs/riot-personal-key-application.md`; passos do
   pedido no [docs/rotacao-chaves.md](docs/rotacao-chaves.md), seção 5 (ação do dono no portal).
2. **Qualquer CDN/proxy novo na frente da API** — reavaliar a sobrescrita de
   `X-Forwarded-For`/`Forwarded` do vhost e a strategy `framework` (os rate limits por IP —
   login e register — dependem disso). Nota "quando revisar" no
   [docs/rotacao-chaves.md](docs/rotacao-chaves.md), seção 3.
3. **CodeQL/setup explícito de code scanning** — o default setup cobre Java+TS há muito; nota
   "quando revisar" no [docs/rotacao-chaves.md](docs/rotacao-chaves.md), seção 3.
4. **Separar roles de migração e de runtime no Neon** — hoje a `campeao_app` é dona das tabelas
   E roda o Flyway; separar role de migração (DDL) de role de runtime (só DML) exige ensaio em
   branch do Neon e confirmação do dono antes de qualquer troca no env da VM.
5. **`/api/auth/register` revela e-mail existente** (409 "E-mail já cadastrado") — o risco de
   enumeração existe e está mitigado pelo rate limit de 5/min por IP no `/register`.
6. **JWT de 7 dias no `localStorage`, sem revogação** — o risco existe: expirar token/logout
   é decisão só do frontend; não há revogação server-side durante os 7 dias de validade.
7. **Refatoração do `RiotService`** (350+ linhas, com resolução de conta duplicada) — pronta a
   rede de segurança: caracterização em `RiotServiceCaracterizacaoTest` (7 pinos, vermelho com
   mutação provada: chave sem `toUpperCase`). A refatoração pode atualizar os pinos
   conscientemente.
8. **`PermitRootLogin` no sshd** (ex.: `prohibit-password`) — endurecimento opcional; estado
    atual, trade-offs e passos de validação/rollback no
    [docs/rotacao-chaves.md](docs/rotacao-chaves.md), seção 4 (aplicação é decisão do dono,
    fora do repositório).

## Pendências resolvidas (arquivo)

- **Verificação de e-mail no cadastro** (decisão do dono: 1b, SMTP com a conta dele, bloqueio
  de login até confirmar; PRs 80, 81 e 82): migração V2 `verificacao_email` (login de
  não-verificado → 403, `/api/auth/verificar` público, `/reenviar-verificacao` limitado a
  5/min por IP), transporte configurável (`LogEmailEnviador` sem `SMTP_HOST` — link no log,
  `SmtpEmailEnvio` com as vars no EnvironmentFile da VM) e frontend: cadastro não loga mais
  (mostra a orientação e volta ao login), login 403 exibe a mensagem do corpo e a rota
  `/verificar?token=` confirma pelo link e aponta para o login. A enumeração do `/register`
  continua mitigada só pelo rate limit (ver pendência 5).

- CSP via `<meta>` no `index.html` (o Pages não dá header HTTP): img-src com Data Dragon e
  Community Dragon, connect-src com a origem da API, scripts só `'self'`, resto fechado;
  cobertura em `frontend/src/csp.spec.ts` (vermelho no index antigo). `frame-ancestors`
  não funciona em `<meta>` — quem muda o `index.html` atualiza o spec junto. O spec lê o
  `index.html` do disco, o que exigiu dev-dependency `@types/node` + `"node"` no `types`
  do `tsconfig.spec.json` (só teste; motivo registrado por ser dependência nova).

- League-V4 migrou de `entries/by-summoner` (removida pela Riot, anúncio de jun/2025) para
  `entries/by-puuid`: o elo é lido com o PUUID da conta, `summoner.id()` nulo não afeta o
  perfil e a chave do cache do ranking virou puuid (`RiotServiceLeaguePuuidTest`, vermelho no
  código antigo).

- Teste de ponta a ponta do `RiotClient` com JSON nulo via servidor de stub
  (`RiotClientJsonNuloE2ETest`): o corpo passa pelo HTTP real do RestClient, provando que os
  conversores de runtime têm a mesma tolerância a `null` dos DTOs (mutação sem `AS_EMPTY`
  falha no teste).

- Postgres descartável local para a suíte de banco real documentado
  ([docs/banco-real-local.md](docs/banco-real-local.md)): container `postgres:17` com a
  mesma configuração da CI, acessado por `DB_URL` nas variáveis de ambiente.

- Runbook de rotação de chaves criado (`docs/rotacao-chaves.md`): passos/rollback da chave da
  Riot e do `JWT_SECRET`, com janelas sugeridas (era a pendência 4; hoje a rotação é manual do
  dono, o runbook é a instrução).
- Visitante que busca invocador é levado para `/login` quando a API responde 401/403
  (PR 67).
- **Auditoria de infra concluída em 2026-11-23** — `docs/auditoria-infra.md` com todas as seções
  verificadas: Neon (role mínima + Flyway em produção), VM (portas, env 600, SSH só chave,
  rpcbind desativado, Security List validada externamente), CORS (origem estranha 403 sem ACAO,
  origem do Pages 200 com ACAO exata), Dependabot, CI com Postgres efêmero, ruleset de proteção.
  `gitleaks git --redact` rodado localmente após o falso positivo do `JwtServiceTest`:
  **no leaks found** (allowlist cirúrgica em `.gitleaks.toml`).
- **Estado de infra confirmado (2026-11-24)**: Neon com duas roles — `campeao_app` (dona das
  tabelas, usada pelo Flyway e pela aplicação) e `campeao_backup` (só leitura, usada pelo backup);
  backup do Neon restaurado e validado em Postgres descartável; VM sem endereço IPv6 global e
  sem nenhum contêiner Docker em execução.
