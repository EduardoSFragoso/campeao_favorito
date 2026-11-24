# Política de Segurança

## Reportando vulnerabilidades

Abra a divulgação **em privado**: GitHub → aba *Security* → *Report a vulnerability*.
Não abra issue pública com detalhes de vulnerabilidade.

## Regras sobre segredos (obrigatório)

- Credenciais (Neon, `RIOT_API_KEY`, `JWT_SECRET`) **nunca** entram no repositório.
  Use variáveis de ambiente / `.env` local (veja `backend/.env.example`).
- Chave de dev da Riot expira em 24h — trate-a como segredo mesmo assim.
- Vazou? Revogue na hora: Riot → *Regenerate key*; Neon → *Reset password*;
  JWT → troque `JWT_SECRET` (invalida todos os tokens).

## Controles aplicados neste projeto

| Área | Controle |
|---|---|
| Atualizações | Dependabot semanal (maven, npm, actions) com agrupamento patch/minor |
| Formato/qualidade | Spotless (Java), ESLint + Prettier (front), build falha se não formatado |
| Auth | BCrypt (custo padrão) + JWT HS256, tokens de 7 dias, sem sessão no servidor |
| XSS | Angular sanitiza por padrão; não usamos `bypassSecurityTrust*` |
| CORS | allowlist explícita (env `CORS_ALLOWED_ORIGINS`), nunca `*` com credenciais |
| Segredos em runtime | placeholder detectado → log WARN e bloqueio de uso real em produção |
| Dependências do CI | vetor de supply-chain minimizado (actions fixadas em tag maior — `checkout@v7`, `setup-java@v6`, `setup-node@v7` etc. — e checadas pelo Dependabot mensal) |

## Checklist de review

- [ ] Nenhum segredo no diff
- [ ] `mvnw verify` verde (compila + spotless)
- [ ] `ng lint` e prettier `--check` verdes
- [ ] Nova rota protegida por auth quando necessário
- [ ] CORS só liberado para origens novas conhecidas
