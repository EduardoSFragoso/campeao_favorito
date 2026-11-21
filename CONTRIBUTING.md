# Contribuindo — campeão_favorito

Regras acordadas com o dono do projeto (Eduardo). **Elas valem para qualquer pessoa humana ou agente de IA trabalhando aqui.**

## 1. Commits sempre aprovados por humano

- **Nunca** fazer `git commit` sem aprovação explícita do dono antes.
- Sempre **mostrar antes** o que vai entrar no commit (`git status`, `git diff --stat`,
  mensagem proposta) e esperar o OK.
- Como agente de IA: encerre o turno apresentando as mudanças e o commit sugerido —
  nunca commitar "em nome de" o dono.

## 2. Mostrar o que foi feito

- Toda mudança relevante vem acompanhada de resumo do que mudou e onde
  (ex.: `backend/.../SecurityConfig.java — novas rotas liberadas do swagger`).
- Escrever mensagens de commit no padrão **Conventional Commits**
  (`feat:`, `fix:`, `chore:`, `docs:`...).

## 3. Segredos ficam fora do repositório

- Nenhuma credencial (senha do Neon, `RIOT_API_KEY`, `JWT_SECRET`) no código, no `yml` de
  config ou em mensagens de commit.
- Valores sensíveis via **variáveis de ambiente** — ver [README.md](README.md) §3.4.
- Modelos de variáveis: `backend/.env.example`.
- Se um segredo vazar por acidente: revogar (Riot: regenerar a key; Neon: resetar a senha;
  JWT: trocar secret) e evitar força bruta no histórico.
- `.gitignore` já cobre `.env*`, mas **verifique sempre** com `git status` antes de aprovar.
- **Regra Riot:** chave de `Development`/`Interim` **não pode ser usada em um app acessível
  pela comunidade** — só em protótipo local. Antes de ativar a VM Oracle, troque a chave por
  uma **Personal** (portal) ou **Production** (app revisado). Mais guard rails de legal no
  [README.md](README.md), seção *"Guard rails da Riot"*.

## 4. Qualidade de código

| Camada | Ferramenta | Comando |
|---|---|---|
| Java | Spotless (google-java-format) | `./mvnw spotless:apply` / `spotless:check` |
| Java | javac com warnings visíveis | `./mvnw -q -DskipTests compile` |
| TS/HTML/SCSS | ESLint (angular-eslint) | `npx ng lint` |
| TS/HTML/SCSS/JSON | Prettier | `npx prettier --write .` / `--check .` |
| Consistência entre editores | EditorConfig | automático |

`mvnw verify` no backend roda `spotless:check` + empaqueta. Uma mudança só está pronta
quando `spotless:check`, `ng lint` e builds passam.

## 5. Dependências

- **Dependabot** roda semanalmente (maven, npm, github-actions) — ver
  [.github/dependabot.yml](.github/dependabot.yml). Aprove PRs de atualização com o próprio
  CI verde; priorize versões de **segurança**.

## 6. Proteção de branch (configuração no GitHub)

O arquivo não consegue proteger branch — precisa ser no GitHub (uma vez após publicar):
*Settings → Rules → Rulesets* para `main`:

- ❗ exigir PR antes de merge;
- ❗ exigir 1 aprovação do CODEOWNERS;
- ❗ exigir *status checks* verdes: `build backend`, `lint & build frontend`, `deploy gh-pages`;
- ❗ bloquear *force push* e deleção;
- ❗ exigir conversação resolvida antes do merge.

Cookie do template: `.github/CODEOWNERS` se aplica automaticamente ao publicar o repo.
