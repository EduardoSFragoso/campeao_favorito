# Draft — Solicitação de Personal API Key (Riot Developer Portal)

> Copie/ajuste os campos abaixo ao preencher a aplicação no dashboard.
> **Estado do projeto:** protótipo local funcionando (chave Development de 24h).
> **Publicação planejada:** frontend em GitHub Pages + backend Spring Boot em VM Oracle
> Cloud (server-side only) — a chave vive SOMENTE no ambiente do backend.

## Product name
campeão_favorito (Champion Favorites)

## Product description (EN)
A hobby, open-source personal project that helps me study the Riot Games Developer Tools.
It allows the user to browse all League of Legends champions (splash arts, loading arts,
lore, abilities and skins served from the official Data Dragon CDN) and mark their
favorites. It also lets players look up **their own** summoner profile by Riot ID: official
ranked tier/division/LP (League-V4 displayed as given by Riot — no alternative rankings,
no MMR/ELO calculators) and their champion mastery top 5 (Champion Mastery-V4).

## Goals
- Enrich the player experience: personal hub for champion browsing and favorites.
- Educational: demonstrate a full-stack integration (Spring Boot + PostgreSQL hosted on
  Neon, Angular) as a portfolio project.
- Data sources: documented Riot API endpoints and Data Dragon only. No scraping, no
  undocumented/undocumented endpoints.

## Riot API usage
- Endpoints used: Account-V1, Summoner-V4, League-V4, Champion Mastery-V4, Champion-V3
  (free-to-play rotation).
- The API key is stored ONLY server-side (environment variable on a VM I control);
  it is never exposed to the frontend or committed to version control.
- Response caching (60s TTL per profile) to respect rate limits.

## Deployment (when public)
- Frontend: static Angular app on GitHub Pages (github.io).
- Backend: Java Spring Boot on an Oracle Cloud VM I own, behind HTTPS; no charge to
  users; no ads; no exclusive access.

## Player-experience safeguards
- No player shaming or evaluation by third parties; summoner lookup is informational
  only, using Riot's official ranking system as-is.
- No use of official Riot logos or alleged endorsement/approval; standard Data Dragon
  attribution is displayed in the app footer.
- Not commercial; no monetization ever.

## Project type
Hobby project / portfolio (individual developer, one project, non-commercial).
