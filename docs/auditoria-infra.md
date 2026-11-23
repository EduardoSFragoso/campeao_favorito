# Auditoria de infraestrutura

Checklist para projetos com **frontend no GitHub Pages, backend em VM (Oracle) e Postgres no Neon**, tudo no mesmo repositório. É referenciado pelo `AGENTS.md`.

**Regra de ouro:** nunca cole segredos reais (senhas, tokens, chaves, `DATABASE_URL` completa). Mascare valores e hosts em qualquer saída enviada a uma IA.

## Ordem das etapas

1. Auditoria **sem alterações** (este documento).
2. Segredos: rotacionar o que estiver exposto, antes de qualquer refatoração.
3. Rede de segurança: testes de caracterização, snapshot ou equivalente.
4. CI e proteção da branch principal.
5. Validação de entrada e contratos da API.
6. Refatorações, uma etapa por vez, com a rede de segurança verde.
7. `handoff.md` para quem continuar.

## 1. GitHub Pages e repositório

- Em **Settings → Pages**, veja a origem do deploy (branch/pasta ou GitHub Action). Só a pasta do frontend deve ser publicada.
- Se a raiz do repositório for publicada, tudo o que estiver nela é baixável por URL, inclusive o código do servidor.
- Procure arquivos sensíveis versionados:

```bash
git ls-files | grep -Ei '\.env|\.pem|\.key|dump|\.sql$|backup'
```

- Procure o mesmo no **histórico** (arquivos que já foram apagados continuam lá):

```bash
git log --all --diff-filter=A --name-only --format= | grep -Ei '\.env|\.pem|\.key|dump|\.sql$' | sort -u
```

- Use um detector de segredos no código e no histórico (por exemplo `gitleaks` ou `trufflehog`), rodando localmente.
- No frontend, só podem existir chaves feitas para serem públicas.

### Se achar um segredo

1. **Rotacione primeiro**: resete a senha da role no Neon, gere uma nova chave SSH ou de API, regenere tokens de terceiros.
2. Só depois decida se vale reescrever o histórico. A rotação é o que realmente protege; apagar do código não basta.

## 2. Neon (Postgres)

No console do Neon, confira:

- plano e **janela de restauração** (antes de qualquer mudança de esquema);
- branches existentes e a role usada pela aplicação;
- o host usado pela aplicação (`-pooler`) e pelas migrações (conexão direta).

Regras:

- `DATABASE_URL` só no servidor, com `sslmode=require`.
- A aplicação usa uma **role com privilégios mínimos**, nunca a dona do banco (`neondb_owner`). As migrações usam outra role.
- Migrações versionadas (Flyway ou Liquibase em Java, Alembic em Python, Prisma Migrate ou node-pg-migrate em Node), testadas do zero na CI com um Postgres de serviço e aplicadas primeiro numa **branch do Neon** criada a partir da produção.

Em Java, a URL JDBC leva `sslmode=require` (`jdbc:postgresql://HOST/BANCO?sslmode=require`). Confira na documentação do Neon as recomendações do driver ao usar o pooler.

Consultas de leitura, para rodar numa branch:

```sql
SELECT current_user, version();

SELECT rolname, rolsuper, rolcreatedb, rolcreaterole
FROM pg_roles WHERE rolname NOT LIKE 'pg_%';

SELECT grantee, table_schema, table_name, privilege_type
FROM information_schema.role_table_grants
WHERE grantee = 'NOME_DA_ROLE_DA_APLICACAO'
ORDER BY table_schema, table_name;
```

## 3. VM Oracle

Comandos de **leitura** para a pessoa executar na VM (mascare IPs e hostnames antes de devolver a saída):

```bash
ss -tlnp
sudo ufw status verbose
sudo sshd -T | grep -Ei 'passwordauthentication|permitrootlogin|pubkeyauthentication'
systemctl list-units --type=service --state=running --no-pager
stat -c '%a %U:%G %n' /etc/NOME_DO_APP/*.env
```

O último comando mostra só as permissões do arquivo de ambiente (o esperado é `600`), sem ler o conteúdo.

No console da Oracle, confira a **Security List / NSG**: só 80 e 443 públicas e SSH restrito.

Verificações de HTTPS e CORS, a partir de qualquer máquina:

```bash
curl -sI https://api.exemplo.com | head -n 5
curl -s -D - -o /dev/null -H 'Origin: https://evil.example' https://api.exemplo.com/health
curl -s -D - -o /dev/null -H 'Origin: https://USUARIO.github.io' https://api.exemplo.com/health
```

- O Pages é https, então a API também precisa ser (domínio e certificado válidos, por exemplo Caddy ou nginx com certbot).
- Para a origem estranha **não** deve voltar `Access-Control-Allow-Origin`. Para a origem do Pages deve voltar exatamente essa origem, nunca `*`.
- Se o login usar cookies entre domínios, use `SameSite=None; Secure` e proteção contra CSRF.

Boas práticas:

- aplicação rodando por `systemd`, com usuário sem privilégios;
- segredos em arquivo de ambiente com permissão 600;
- atualizações automáticas do sistema;
- deploy com uma chave dedicada e de escopo restrito, guardada em GitHub Environments, e não a chave SSH pessoal.

## 4. Backend

- Autenticação e autorização em toda rota que não seja pública.
- Validação de toda entrada e consultas parametrizadas (contra SQL injection).
- Limite de requisições nas rotas sensíveis.
- Logs sem dados pessoais (LGPD) e respostas de erro sem stack trace.
- Dependências: `npm audit` (Node), `pip-audit` (Python) ou OWASP Dependency-Check (Java/Maven), com Dependabot ativo (suporta npm, pip, Maven e GitHub Actions).

### Projetos Java (Spring Boot + Maven)

**Pontos de atenção**

- **Actuator:** em produção só o endpoint `health`; `management.endpoints.web.exposure.include=*` expõe `env`, `heapdump` e outros.
- **Spring Security:** revise a `SecurityFilterChain`. Toda rota sensível exige autenticação, e a lista de `permitAll` deve ser curta. CSRF só pode ficar desligado em API sem sessão por cookie. Com cookies entre domínios, mantenha a proteção.
- **CORS:** configure por `CorsConfigurationSource` com a origem do Pages. Nada de `@CrossOrigin("*")` nem `allowedOrigins("*")` junto com credenciais.
- **Credenciais:** em `application*.properties` e `.yml` use placeholders (`${VARIAVEL}`), nunca valores. O perfil de produção lê tudo do ambiente da VM.
- **SQL:** parâmetros nomeados ou `PreparedStatement`; nunca concatenar entrada do usuário.
- **Logs:** `spring.jpa.show-sql` desligado em produção e sem dados pessoais.
- **Build:** `target/` fora do Git.

**Conexão com o Neon.** A connection string do Neon começa com `postgresql://usuario:senha@host/banco`, que o JDBC não aceita direto. No Spring Boot, separe em três variáveis de ambiente:

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST}/${DB_NAME}?sslmode=require
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
```

Para migrações com a role separada (privilégios maiores que os da aplicação), o Spring Boot permite credenciais próprias do Flyway:

```properties
spring.flyway.user=${DB_MIGRATION_USER}
spring.flyway.password=${DB_MIGRATION_PASSWORD}
```

Confira na documentação do Neon as recomendações do driver ao usar o pooler.

**Comandos de leitura**, na raiz do repositório:

```bash
grep -rEn 'management\.endpoints\.web\.exposure\.include|spring\.datasource\.password *=|password *=|secret *=' src/main/resources
grep -rEn 'csrf|permitAll|anyRequest|authorizeHttpRequests' src/main
grep -rEn '@CrossOrigin|allowedOrigins' src/main
grep -rEn 'show[-_]sql' src/main/resources
grep -rEn 'createStatement\(|"(SELECT|INSERT|UPDATE|DELETE) [^"]*" *\+' src/main
./mvnw -q dependency:tree
./mvnw versions:display-dependency-updates
./mvnw org.owasp:dependency-check-maven:check
```

O Dependency-Check pode pedir uma chave de API da NVD para rodar rápido. O Dependabot (`maven`) abre os PRs de atualização, inclusive do Spring Boot.

**Testes e CI**

- `./mvnw verify` roda os testes e as verificações do build; é o comando da CI e o que se executa antes de concluir uma tarefa.
- Testes de caracterização das rotas com JUnit 5 e `MockMvc` (ou `@SpringBootTest`); Postgres de teste via Testcontainers (no Spring Boot 3.1 ou superior, com `@ServiceConnection`) ou serviço da CI.
- Na CI, instale o JDK da versão do projeto com cache do Maven:

```yaml
- uses: actions/setup-java@v5 # troque pela versão atual mantida pelo Dependabot
  with:
    distribution: temurin
    java-version: "21" # use a versão do projeto
    cache: maven
- run: ./mvnw -B verify
```

## 5. Rede de segurança por camada

| Camada       | Proteção                                                                                                                                                                                                              |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Frontend     | Snapshot de renderização (ex.: jsdom), testes dos pontos de injeção de HTML e verificação de carga                                                                                                                    |
| Backend      | Testes de caracterização das rotas (gravam as respostas atuais e provam que não mudaram), testes de integração (em Spring Boot: JUnit 5 com MockMvc e Postgres via Testcontainers ou serviço da CI), análise estática |
| Banco        | Migrações testadas do zero na CI com Postgres efêmero                                                                                                                                                                 |
| Dependências | Dependabot, `npm audit` ou `pip-audit`                                                                                                                                                                                |

Confirme que cada teste novo **falha** no código antigo antes de confiar nele.

## 6. CI e deploy do Pages

- A CI gera exatamente o que será publicado no Pages.
- `permissions` mínimas (`contents: read`); as GitHub Actions ficam atualizadas.
- Proteção da branch principal: PR, check obrigatório e método de merge definido no `handoff.md`.
- PRs abertos por workflows com o `GITHUB_TOKEN` (autor `github-actions[bot]`) ficam em _Action required_ e só rodam a CI depois de **Approve workflows to run**.

## O que o agente não deve fazer sem confirmação

DROP, TRUNCATE, apagar volumes ou branches, alterar firewall ou Security List, reiniciar serviços, aplicar migração na produção, reescrever o histórico do Git ou fazer push forçado.

## Estado da auditoria no `campeao_favorito` — 2026-11-23

Resultado da última auditoria completa (código + histórico + API do GitHub). Reexecutar os comandos das seções acima após mudanças estruturais e atualizar esta seção.

### GitHub Pages e repositório

- Deploy publicado **só com o frontend**: os arquivos da raiz (`README.md`, `handoff.md`) e do backend (`application.yml`) voltam 404 no site; HTTPS reforçado.
- Origem do Pages em _Settings_ mostra "branch `main` /" — a publicação real é pelo workflow `deploy-gh-pages.yml` (artefato). Pendente cosmético: trocar a origem para _GitHub Actions_.
- Nenhum arquivo sensível versionado nem no histórico (só a migração Flyway, legítima). Gitleaks limpo em código e histórico (`gitleaks git --redact -v`); falso positivo do `JwtServiceTest` coberto pelo `.gitleaks.toml`.
- Secret scanning e push protection **ativados**; Dependabot security updates **ativados** (além do `dependabot.yml` semanal).
- `monitor.yml` (health check agendado) contém o endereço do backend — trade-off consciente de repo público; o IP real também aparece em logs de execução.

### Backend (Spring Boot)

- Actuator sem exposição ampla (só `/health`), `show-sql` desligado, sem `createStatement` nem SQL concatenado, Swagger desligado no perfil `prod`.
- `permitAll` curto (`/api/auth/**`, `/api/health`, `/error`); CSRF off é ok (API JWT sem cookie).
- CORS com allowlist via env (`CORS_ALLOWED_ORIGINS`), sem `*`; JWT com placeholder e fail-fast em produção; rate limit no login (`LoginRateLimiter`).
- Placeholders em todos os `application*.yml` (`${DB_URL}`, `${JWT_SECRET}`...); nada de credencial versionada.
- Pendência: logs sem dados pessoais revisados só pontualmente (sem varredura dedicada).

### Neon

- `DATABASE_URL` só no EnvironmentFile da VM (permissão 600, fora do Git), com `sslmode=require`.
- Migrações Flyway versionadas e testadas do zero na CI com Postgres efêmero.
- Pendência: **backup** — desenho aprovado e adiado (Action agendada com `pg_dump` criptografado, ver conversa de 2026-11-23); até lá, `pg_dump` manual antes de qualquer migração.

### VM

- sshd: `passwordauthentication no`; env da app com `600`; rpcbind desativado; saúde via `monitor.yml`.
- Pendências de endurecimento (baixa prioridade): `ufw` não instalado (a Security List da Oracle faz o papel — conferir no console: só 80/443 públicas, SSH restrito); Java escuta em `*:8080` (a nuvem bloqueia de fora, mas vale mudar o bind para 127.0.0.1); deploy continua manual (sem chave dedicada em Environment).
