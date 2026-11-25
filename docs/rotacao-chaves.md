# Runbook — rotação de chaves (Riot API e JWT_SECRET) e revisões agendadas

Procedimentos manuais que hoje dependem do dono. Nenhum comando aqui altera a VM por si só:
os que mexem em produção estão marcados com ⚠️ e SÓ devem ser executados pelo dono, com
backup e prova depois (regra do AGENTS.md). Sem segredos em logs, mensagens ou neste
documento — onde aparecem, mascarar as saídas.

## 1. Rotação da chave da Riot API

**Janela sugerida:** a cada renovação do portal (a chave pessoal não expira em 24h como a de
desenvolvimento, mas o Regenerate invalida a anterior) ou imediatamente em caso de suspeita
de vazamento. Duração percebida pela API: os minutos de um restart.

### Passos

1. No [developer.riotgames.com](https://developer.riotgames.com), Dashboard → PERSONAL API KEY
   → *Regenerate*. Copie a chave nova **sem** colar em nenhum arquivo versionado, chat ou
   ticket.
2. ⚠️ Na VM, backup do env antes de editar (mantém o rollback de segundos):
   ```bash
   sudo cp /etc/campeao-favorito.env /etc/campeao-favorito.env.bak
   sudo chmod 600 /etc/campeao-favorito.env.bak
   ```
3. ⚠️ Edite `/etc/campeao-favorito.env` (é o `EnvironmentFile` do
   `campeao-favorito.service`; permissão 600) trocando o valor de `RIOT_API_KEY`. Use editor
   com o caminho completo (`sudo nano /etc/campeao-favorito.env`); o arquivo não é link.
4. Confirme qual unidade e arquivo estão em uso antes de qualquer outra coisa:
   ```bash
   systemctl cat campeao-favorito | grep -i "EnvironmentFile\|ExecStart"
   ```
5. ⚠️ Reinicie o serviço:
   ```bash
   sudo systemctl restart campeao-favorito
   ```
6. **Prova depois** (log limpo de 403 e app no ar):
   ```bash
   sudo journalctl -u campeao-favorito --since "2 min ago" --no-pager
   curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/api/health
   ```
   O `/api/health` é healthcheck independente: 200 confirma o app de pé; uma chamada real de Riot
   (rotação semanal pela UI) com 200 confirma a chave nova; `403` com a mensagem "Chave da Riot
   API inválida ou expirada" indica chave ruim — olhar log, não adivinhar.

### Rollback

```bash
sudo cp /etc/campeao-favorito.env.bak /etc/campeao-favorito.env
sudo systemctl restart campeao-favorito
```
Só necessário se a chave nova falhar (ex.: Regenerate feito duas vezes e a antiga ainda em
uso). Após estabilidade, apagar o `.bak` (`sudo shred -u`).
Depois de qualquer rotação: não comunicar a chave a ninguém; o frontend nunca a vê
(só o backend chama a Riot).

## 2. Rotação do JWT_SECRET

**Janela sugerida:** trimestral ou quando rotacionar pessoas com acesso à VM; e
imediatamente em suspeita de vazamento. Consequência conhecida e desejada: **todas as
sessões caem** (tokens assinados com a chave antiga falham na validação → 401 → o frontend
leva ao `/login`, comportamento do PR 67). Fazer numa janela de pouco tráfego.

### Passos

1. Gere a nova (comprimento mínimo 32 bytes — o `JwtService` recusa o que for menor em
   qualquer perfil):
   ```bash
   openssl rand -base64 64
   ```
   O valor só existe na VM — nunca versionado nem colado em chat/ticket.
2. ⚠️ Mesmo backup do passo 2 da seção 1 (o `.env.bak` também serve o rollback do JWT).
3. ⚠️ Troque o valor de `JWT_SECRET` no `/etc/campeao-favorito.env`.
4. ⚠️ `sudo systemctl restart campeao-favorito`.
5. Prova: log sem `IllegalStateException` de boot e sem o warning do placeholder; login real
   devolve token novo e uma requisição autenticada com token ANTIGO deve receber 401.

### Rollback

Igual ao da seção 1 (restaurar `.env.bak` e reiniciar). Tokens emitidos no intervalo caem —
seguro do ponto de vista do usuário.

## 3. Notas de "quando revisar" (gatilho, não tarefa)

- **CDN/proxy novo na frente da API (pendência 2):** revalidar o vhost (a sobrescrita de
  `X-Forwarded-For` com `$remote_addr` e o header `Forwarded` zerado) e a
  `server.forward-headers-strategy=framework`. Os rate limits por IP (login e register)
  só são corretos com o nginx como ÚNICO proxy — revisar ANTES de ligar qualquer CDN,
  não depois. Referência: testes do PR 58.
- **Code scanning no ruleset (pendência 5):** o default setup (CodeQL) já cobre Java+TS.
  Se um dia o `Require code scanning results` entrar no ruleset `protege-main`, medir os
  tempos de análise antes (job zerou? 6 min na CI?) e avaliar o impacto em PRs pequenos.

## 4. sshd — `PermitRootLogin` (pendência 14): leitura e decisão do dono

Estado atual no servidor (comandos de LEITURA — nada altera):

```bash
sudo sshd -T | grep -i permitrootlogin   # configuração efetiva
sudo grep -Rin "PermitRootLogin" /etc/ssh/ /etc/ssh/sshd_config.d/ 2>/dev/null || echo "(não definido — default OpenSSH: prohibit-password)"
sudo journalctl -u ssh --since "30 days ago" | grep -c "Failed password for root" || true
```

- **Opção — `PermitRootLogin prohibit-password`** (mais duro que `yes`, ainda permite login
  por chave explícita): remove o vetor de senha do root enquanto mantém o acesso de
  administração por chave que o dono já usa (o login já é só por chave hoje — `PasswordAuthentication`
  desligado na VM; verificar com `sudo sshd -T | grep -i passwordauthentication`).
- A mudança envolve ⚠️ editar `/etc/ssh/sshd_config` (ou um arquivo em
  `/etc/ssh/sshd_config.d/`), validar com `sudo sshd -t`, reiniciar `ssh` e **manter a sessão
  atual aberta** enquanto prova um novo login por chave numa segunda sessão. Rollback = comentar
  a linha e reiniciar de novo. **Aplicar é decisão do dono e não entra em PR nenhum deste
  repositório.**

## 5. Chave de produção pessoal da Riot (pendência 1) — ação do dono

Pedido no [developer.riotgames.com](https://developer.riotgames.com) (registro de produto —
a navegação exata do dashboard pode ter mudado; o texto pronto para os campos está em
`docs/riot-personal-key-application.md`). O que a Riot avalia por lá: descrição do projeto,
URL pública (a do GitHub Pages), escopo das rotas usadas (Account-V1, Summoner-V4,
League-V4, Champion Mastery-V4, Champion-V3), política de privacidade e a menção de que há
login próprio — dados pessoais só de quem logou voluntariamente. Não afirmar
endosso/parceria (guard rails do README). Sem prazo fixo da Riot; monitorar o e-mail da
conta do portal. Enquanto a resposta não chega, a chave pessoal atual segue sendo suficiente
— único efeito da demora é o elo ranqueado continuar ocultado pelo degrade.
