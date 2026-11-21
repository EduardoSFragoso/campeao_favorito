# ⚔️ campeão_favorito

Explore campeões de **League of Legends** com splasharts, lore, skills e skins (Data Dragon),
**favorite** com login próprio (JWT + Neon Postgres) e **busque invocadores** com dados ao vivo
da **Riot Games API** — elo ranqueado, nível e top maestria.

## Arquitetura

```
┌─────────────────────┐        ┌──────────────────────────┐        ┌───────────────┐
│  GitHub Pages       │  HTTP  │  Spring Boot (backend/)  │  JDBC  │  Neon         │
│  Angular 21 (SPA)   │ ─────▶ │  Java 25 · JWT · Swagger │ ─────▶ │  Postgres     │
└─────────┬───────────┘        └────────────┬─────────────┘        └───────────────┘
          │   splasharts/lore   (CDN direta, sem chave)
          ▼
   Data Dragon (ddragon.leagueoflegends.com)
```

| Camada | Tecnologia | Onde roda |
|---|---|---|
| Frontend | Angular 21 (signals, standalone) | GitHub Pages (estático) |
| Backend | Spring Boot 4.2 · Java 25 · Lombok · DevTools · springdoc OpenAPI | local (8080) — **futuro: VM Oracle Cloud** |
| Banco | PostgreSQL 17 serverless | Neon (free tier) |
| Dados de jogo | Data Dragon (sem chave) + Riot API (com chave) | CDN / externo |

> **Importante:** o GitHub Pages serve apenas arquivos estáticos. O backend precisa rodar em
> outro lugar. **Plano de hospedagem do backend: VM Oracle Cloud Free Tier** (Ubuntu, JDK 25 +
> systemd + nginx/TLS) — ver §6. Enquanto isso, roda local. Splasharts e infos de campeões
> **não usam a Riot API** — vêm do Data Dragon, que é aberto e ilimitado. A Riot API entra para
> dados *ao vivo* (invocador, elo, maestria, rotação).

## Pré-requisitos

- **Java 25** (JDK) — `java -version`
- **Node 24.15+** — já atualizado no seu computador (24.22.3)
- Contas: [Neon](https://neon.tech) e [Riot Developer Portal](https://developer.riotgames.com)

## 1. Neon Postgres

1. Crie um projeto em [neon.tech](https://neon.tech) (free tier, serverless).
2. No painel, copie a **connection string** do formato :
   `postgresql://user:senha@ep-xxx-pooler-xxx.aws.neon.tech/neondb?sslmode=require`
3. Converta para JDBC (formato que o Spring espera):
   `jdbc:postgresql://ep-xxx-pooler-xxx.aws.neon.tech/neondb?sslmode=require`

As tabelas (`users`, `favorites`) são criadas automaticamente no boot (`ddl-auto: update`).

## 2. Chave da Riot API

> ⚖️ **Regra dos Developer Tools:** a chave de desenvolvimento é para **protótipo
> local/inacessível pela comunidade**. O projeto só pode ser publicado (GitHub Pages + VM)
> com chave **Personal** ou **Production** registrada no portal.

1. Login no [developer.riotgames.com](https://developer.riotgames.com).
2. Dashboard → **PERSONAL API KEY** → *Generate Key* (**expira em 24h** — gere de novo por
   *Regenerate* quando expirar; o backend mostra erro `403` amigável quando isso acontece).
3. **Desenvolvimento local (fase atual):** chave de Development serve
   — nada é público ainda.
4. **Antes de publicar (VM Oracle):** solicite a chave **Personal** (protótipo individual) no
   próprio portal — ela não expira em 24h e cobre projetos pessoais não-comerciais. Para
   escala/produção, a **Production Key** exige *application* com revisão da Riot
   (1 chave por projeto; nada de afirmar endosso/parceria).
5. A chave **nunca** sai do backend (`RIOT_API_KEY` via env). O front não a acessa jamais.

## 3. Rodando o backend

```bash
cd backend
```

No PowerShell (ou defina no seu `.zshrc`/profile):

```powershell
$env:DB_URL = "jdbc:postgresql://ep-xxx-pooler-xxx.aws.neon.tech/neondb?sslmode=require"
$env:DB_USER = "<usuario>"
$env:DB_PASSWORD = "<senha>"
$env:RIOT_API_KEY = "RGAPI-xxxx"
$env:JWT_SECRET = "gere-com: openssl rand -base64 64"
./mvnw.cmd spring-boot:run
```

Swagger UI com testes de endpoint embutidos: **http://localhost:8080/swagger-ui.html**

## 4. Rodando o frontend

```bash
cd frontend
npm install
npm start   # http://localhost:4200
```

Em dev ele lê `frontend/public/config.json` apontando para `http://localhost:8080`.
CORS já liberado para `http://localhost:4200` (variável `CORS_ALLOWED_ORIGINS`).

## 5. Publicar no GitHub Pages

1. Suba o repositório e ative *Settings → Pages → Source: GitHub Actions*.
2. Crie a variável de repositório `API_URL` com a URL **pública** do backend
   (ex.: `https://api.seudominio.com` — na fase da VM Oracle será o IP/domínio dela) —
   o workflow reescreve `config.json` no deploy.
3. Push em `main` → o workflow [deploy-gh-pages.yml](.github/workflows/deploy-gh-pages.yml)
   faz build com `--base-href "/<repo>/"` e publica.

## 6. Backend na VM Oracle Cloud (futuro próximo)

Logo o backend sai do seu computador para uma **VM Oracle Cloud Always Free**
(ideal: Ubuntu + Ampere A1; alternativo: E2.1.Micro). Checklist planejado:

1. Criar a VM (região perto do server BR) e liberar *Security List*: 22 (seu IP) + 443/80.
2. Ubuntu: `apt install openjdk-25-jdk-headless nginx certbot python3-certbot-nginx`.
3. Deploy do jar (`./mvnw clean package -DskipTests` → `scp target/*.api-0.0.1-SNAPSHOT.jar`).
4. Rodar via **systemd**, com segredos num arquivo `/etc/campeao-favorito.env` (chown root:600):
   o mesmo conjunto de variáveis do §3 (`DB_URL`, `JWT_SECRET`, `RIOT_API_KEY`,
   `CORS_ALLOWED_ORIGINS=https://<user>.github.io`).
5. **nginx** como reverse proxy (8080 → 443) + **Certbot** para TLS (`apt` serve 80/443;
   nenhum IP precisa estar no CORS além do do Pages).
6. Atualizar a variável `API_URL` do repositório (ex.: `https://dominio-ou-ip`) e reprocessar
   o deploy do Pages.

Quando configurar a VM, me chame: eu preparo os scripts de deploy (`systemd unit`, cópia)
e um workflow opcional de *deploy contínuo* para a VM (via SSH).

## Endpoints

Todos (exceto auth/health/swagger) exigem `Authorization: Bearer <token>` do login.

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/register` | `{email, password, displayName}` → token JWT |
| POST | `/api/auth/login` | `{email, password}` → token JWT |
| GET | `/api/favorites` | Lista favoritos do usuário |
| POST | `/api/favorites` | `{championId, championName, title}` |
| DELETE | `/api/favorites/{championId}` | Remove |
| GET | `/api/riot/summoner?gameName=&tagLine=&platform=` | Perfil + elo + top 5 maestria |
| GET | `/api/riot/rotation?platform=` | Rotação semanal (Champion-V3) |
| GET | `/api/health` | Favicon de runbook (up) |

... e o resto da Riot API (match, spectator, clash, status...) — alguns já desenhados no
código: `RiotClient` aceita rotas de servidor e rotas regionais; estenda em `RiotService`.

## Stack técnica (resumo)

**Backend:** controller advice global para erros; BCrypt + jjwt (HS256); `RestClient` (Spring 7)
com cache TTL por endpoint; Lombok (`@Getter/@Setter/@RequiredArgsConstructor`); DevTools para
reload quente; springdoc 3.x (Swagger UI + esquema Bearer no Authorize).

**Frontend:** Angular 21 com signals (`toSignal`), standalone components, novo `@for/@if`,
`provideAppInitializer` para `config.json` (mesmo binário dev/prod), interceptor JWT e
`withHashLocation()` para funcionar no GitHub Pages sem rewrite.

## O que dá para construir em seguida com a Riot API

- **Match history** (`MATCH-V5`): últimos 20 jogos, builds, KDA, winrate — serve "o que o pro
  abriu de build em tal partida".
- **Timeline** (`MATCH-V5` timeline): posição por minuto → heatmaps de kills/ganks.
- **Spectator** (`SPECTATOR-V5`): "quem está jogando agora" e featured games.
- **Clash** (`CLASH-V1`): torneios e times.
- **Status** (`STATUS-V4`): banner "servidor estável/incidente" no app.
- **Match vs elo**: puxar partidas ranqueadas de um elo e comparar picks/winrates.

### ⚖️ Guard rails da Riot (Developer Tools) para features futuras

- 🚫 Calculadoras de MMR/ELO ou qualquer ranqueamento alternativo ao rankeado oficial.
- 🚫 Recursos que "shamem" jogadores por métricas de performance (sem badges de
  "jogador ruim", sem avaliações/submissões por terceiros).
- 🚫 Endpoints não documentados: somente RIOT API especificada + Data Dragon.
- 🚫 Logos oficiais da Riot/LoL (o favicon e o tema são próprios e não-copiados).
- 🚫 Em nenhuma hipótese alegar endosso/parceria com a Riot em material do projeto.
- ✅ Busca de invocador permanece apenas informativa (sistema oficial: tier/rank/LP).

*Legal:* dados de jogo e conteúdo — © Rift/Riot Games; seu uso respeita os termos da
[Legal Jibber Jabber](https://www.riotgames.com/en/legal) da Riot (*Fan Kit* / *League API*).
