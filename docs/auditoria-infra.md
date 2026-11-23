# Auditoria de infraestrutura

Checklist para projetos com **frontend no GitHub Pages, backend em VM (Oracle) e Postgres no Neon**, tudo no mesmo repositório. É referenciado pelo `AGENTS.md`.

Os comandos e nomes abaixo já preenchidos com os valores reais deste projeto (`campeao_favorito`); o resultado da última auditoria está na seção **Estado da auditoria**, no fim do documento.

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
- Migrações versionadas (Alembic, Prisma Migrate, Flyway, node-pg-migrate etc.), testadas do zero na CI com um Postgres de serviço e aplicadas primeiro numa **branch do Neon** criada a partir da produção.

Consultas de leitura, para rodar numa branch:

```sql
SELECT current_user, version();

SELECT rolname, rolsuper, rolcreatedb, rolcreaterole
FROM pg_roles WHERE rolname NOT LIKE 'pg_%';

SELECT grantee, table_schema, table_name, privilege_type
FROM information_schema.role_table_grants
WHERE grantee = 'campeao_app'
ORDER BY table_schema, table_name;
```

## 3. VM Oracle

Comandos de **leitura** para a pessoa executar na VM (mascare IPs e hostnames antes de devolver a saída):

```bash
ss -tlnp
sudo sshd -T | grep -Ei 'passwordauthentication|permitrootlogin|pubkeyauthentication'
systemctl list-units --type=service --state=running --no-pager
stat -c '%a %U:%G %n' /etc/campeao-favorito.env
```

O último comando mostra só as permissões do arquivo de ambiente (o esperado é `600`), sem ler o conteúdo.

No Ubuntu da Oracle **não há `ufw`**; o firewall na máquina é `iptables` (`sudo iptables -S` para leitura), e o filtro de verdade é a Security List do console.

No console da Oracle, confira a **Security List / NSG**: só 80 e 443 públicas e SSH restrito.

Verificações de HTTPS e CORS, a partir de qualquer máquina:

```bash
curl -sI https://147-15-57-6.sslip.io | head -n 5
curl -s -D - -o /dev/null -H 'Origin: https://evil.example' https://147-15-57-6.sslip.io/api/health
curl -s -D - -o /dev/null -H 'Origin: https://eduardosfragoso.github.io' https://147-15-57-6.sslip.io/api/health
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
- Dependências: `npm audit` ou `pip-audit`, com Dependabot ativo.

## 5. Rede de segurança por camada

| Camada | Proteção |
|---|---|
| Frontend | Snapshot de renderização (ex.: jsdom), testes dos pontos de injeção de HTML e verificação de carga |
| Backend | Testes de caracterização das rotas (gravam as respostas atuais e provam que não mudaram), testes de integração, análise estática |
| Banco | Migrações testadas do zero na CI com Postgres efêmero |
| Dependências | Dependabot, `npm audit` ou `pip-audit` |

Confirme que cada teste novo **falha** no código antigo antes de confiar nele.

## 6. CI e deploy do Pages

- A CI gera exatamente o que será publicado no Pages.
- `permissions` mínimas (`contents: read`); as GitHub Actions ficam atualizadas.
- Proteção da branch principal: PR, check obrigatório e método de merge definido no `handoff.md`.
- PRs abertos por workflows com o `GITHUB_TOKEN` (autor `github-actions[bot]`) ficam em *Action required* e só rodam a CI depois de **Approve workflows to run**.

## O que o agente não deve fazer sem confirmação

DROP, TRUNCATE, apagar volumes ou branches, alterar firewall ou Security List, reiniciar serviços, aplicar migração na produção, reescrever o histórico do Git ou fazer push forçado.

**Regra adicional deste projeto:** quem verifica e aperta o merge é o dono. O agente abre o PR, espera a CI ficar verde e **para** — nunca aprova nem faz merge por conta própria (ver `CONTRIBUTING.md`).

## Estado da auditoria neste projeto (23/11/2026)

Auditoria completa encerrada. Resumo do que foi verificado (detalhes e comandos no `handoff.md`):

- **Repositório:** nenhum segredo no código nem no histórico (`gitleaks git --redact -v` = *no leaks found*; falso positivo do `JwtServiceTest` silenciado por `.gitleaks.toml` na raiz + `gitleaks:allow` inline).
- **Neon:** produção no projeto correto (`ep-rapid-resonance-*`); role mínima `campeao_app` (sem CREATEDB/CREATEROLE, CREATE restrito ao schema public); migração Flyway aplicada e validada ponta a ponta. O projeto errado (`ep-tiny-glitter`) foi esvaziado e sua branch deletada.
- **VM:** só 22/443/8080 no `ss -tlnp`; 8080 bloqueado de fora pela Security List (timeout de fora); rpcbind desativado; env com `600 ubuntu:ubuntu`; SSH só por chave.
- **CORS/HTTPS:** origem estranha → 403 sem `Access-Control-Allow-Origin`; origem do Pages → 200 com ACAO exata.
- **CI/branch:** ruleset `main-protection` ativo (PR obrigatório, os dois jobs do `ci.yml` como checks, linear history, rebase only); Dependabot ativo; CI testando Flyway do zero com Postgres efêmero.

Pendência conhecida: chave de produção da Riot (elo ranqueado hoje roda em modo degradado).
