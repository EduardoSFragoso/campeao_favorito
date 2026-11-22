# Fase 4 — Deploy em produção (Oracle Cloud VM)

Como o projeto foi para o ar: backend Spring Boot na VM OCI, front no GitHub
Pages, banco Neon, TLS via acme.sh e o caminho das pedras. Diagnóstico
reproduzível: sempre por comandos que imprimem **apenas metadados** (len,
status HTTP), nunca segredos.

---

## Arquitetura no ar

| Camada | Onde | Detalhe |
|---|---|---|
| Front (Angular 21) | GitHub Pages | `https://eduardosfragoso.github.io/campeao_favorito/` — deploy automático no merge em `main` |
| API URL do front | Variável do repo `API_URL` | `frontend/public/config.json` é **reescrito no build** do deploy — trocar backend = PATCH da variável + 1 dispatch, sem PR |
| Backend (Spring Boot 4.2 / Java 25) | VM Oracle (E2.1.Micro, Ubuntu) | `https://147-15-57-6.sslip.io` — nginx 443 → proxy → `127.0.0.1:8080` |
| Banco | Neon Postgres (nuvem) | não roda nada de banco na VM |
| Riot API | chaves de dev | ver "Saga da Riot API" |

---

## O quebra-cabeça da porta 80 (leitura obrigatória)

**Sintoma:** porta 80 não responde de fora, chame de onde chamar.

**Investigação:** SL correta + anexada à instância, SEM NSG, host limpo
(`iptables -L INPUT` vazio, policy ACCEPT), testado de 2 fontes distintas +
validadores LE. Conclusão: **a rede OCI derruba pacotes na 80 a montante** —
não chegam na VM. Não é problema configurável do lado da instância.

**Decisão:** não insistir na 80. O de-para definitivo é:
- HTTP só sob HTTPS;
- desafios ACME resolvidos com **TLS-ALPN-01 na 443** (abaixo).

---

## TLS com acme.sh (TLS-ALPN-01)

Cert emitido e renovado com acme.sh instalado **para o root**:

- Instalação em `/root/.acme.sh/`
- Desafio **standalone `--alpn` na 443** (requisito: nginx parado durante o
  desafio).
- **Armadilha:** o acme.sh *aborta se invocado com sudo*. Logar com
  `sudo su -` e rodar dentro do shell root.
- Instalação do cert emitido no nginx:
  ```bash
  acme.sh --installcert -d 147-15-57-6.sslip.io \
    --keypath  /etc/nginx/ssl/sslip.key \
    --fullchainpath /etc/nginx/ssl/sslip.crt \
    --reloadcmd "systemctl reload nginx"
  ```
  O cron de renewal do root cuida da renovação.

### nginx

Vhost `/etc/nginx/sites-available/campeao-favorito`:
- `listen 443 ssl` com os cert acima;
- `proxy_pass http://127.0.0.1:8080;`
- A porta 80 continua servida pelo `default site` (irrelevante: a OCI bloqueia).

A **raiz `/` ficou como está** (403/Whitelabel do Spring) — decisão do dono do
projeto; o front vive no GitHub Pages e só consome `/api/*`.

---

## systemd do backend

`/etc/systemd/system/campeao-favorito.service`:

```
ExecStart=/usr/bin/java -jar /opt/campeao-favorito/app.jar
User=ubuntu
EnvironmentFile=/etc/campeao-favorito.env
Restart=always
```

### `/etc/campeao-favorito.env`

Contém `RIOT_API_KEY` (e o que mais o backend precisar). Trocar a chave da
Riot (expira em 24h):

```bash
nano /etc/campeao-favorito.env
sudo systemctl restart campeao-favorito
```

**Notas de diagnóstico (sem expor segredos):**
- `len=42` é **normal** para dev key (`RGAPI-` + 36 hex);
- checar duplicatas com `awk -F= '/RIOT_API/ {print NR": "$1" len="length($2)}'`
  (cursor em `< /dev/null` para o awk não pendurar no stdin);
- para testar a chave **em memória do processo**:
  ```bash
  PID=$(systemctl show -p MainPID --value campeao-favorito)
  K=$(sudo tr '\0' '\n' < /proc/$PID/environ | \
      awk -F= '/^RIOT_API_KEY=/ {print substr($0, index($0,"=")+1)}')
  curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "X-Riot-Token: $K" \
    "https://br1.api.riotgames.com/lol/status/v4/platform-data"
  ```

---

## Processo de deploy do backend

Build **local** (não existe repo na VM):

```bash
# no Windows
./mvnw -B package -DskipTests        # backend/target/api-0.0.1-SNAPSHOT.jar
# upload via MobaXterm SFTP para /tmp/
```

Na VM:

```bash
sudo mv /tmp/api-0.0.1-SNAPSHOT.jar /opt/campeao-favorito/app.jar
sudo systemctl restart campeao-favorito
```

**Traço da VM:** o Tomcat leva ~60s para subir no E2.1.Micro — um `502`
transitório no health logo após o restart/reboot é só timing, aguardar.

Deploy do frontend: automático no merge em `main` (workflow de Pages), com a
variável `API_URL` do repo como fonte de verdade.

---

## Saga da Riot API (o 403 do "Chave inválida")

Cadeia de diagnóstico que fechou o caso (PRs #16–#18):

1. **Logging de erros** (`RiotClient` loga WARN com host + path + status —
   PR #16): a chave nunca loga (só header).
2. Teste completo com curl na VM usando a chave do env **e a chave em memória
   do processo** → account-v1, summoner-v4 e mastery **200**; só
   `/lol/league/v4/entries` → **403**.
3. Conclusões:
   - **League-V4 é negado (403) pela Riot para chaves de dev** — mesmo com a
     chave válida. O perfil degrada sem ranqueada (PR #17); o elo voltaria
     apenas com chave de produção.
   - A Riot **removeu `chestGranted`** do champion-mastery-v4 (baús saíram do
     jogo em 2025) e mudou o formato do JSON — `MasteryDto` ficou tolerante
     (wrappers + `ignoreUnknown`, PR #18).
   - "Não encontrado" (404) p/ contas que não existem na plataforma da busca
     é resposta legítima da Riot (ex.: contas de KR buscadas em BR).

Busca de invocador no front: **campo único** `GameName#TAG` (ou só o nome com
a região selecionada), com aliases de tag (`BR`→`BR1`, `KR`→`KR`) e
plataforma derivada da tag (PRs #20, #21).

---

## Hardening do firewall (fechado)

Estado final: **INPUT DROP** com apenas:
1. `-i lo` (loopback)
2. `ESTABLISHED,RELATED`
3. `icmp echo-request`
4. TCP 22 (SSH)
5. TCP 80 (bloqueado a montante pela OCI mesmo assim; deixado por futuro)
6. TCP 443 (nginx)

`FORWARD`/chains `DOCKER-*` intocados (Docker se autogere via nft).

Persistência — **a pegadinha que custou um reboot extra:**

- `iptables-persistent` instalado e `netfilter-persistent save` roda, mas o
  serviço estava **`disabled`** → o boot voltava com policy ACCEPT e regras
  vazias.
- Fix: `sudo systemctl enable netfilter-persistent` (cria symlink em
  `multi-user.target.wants`) + re-reboot de prova: rules carregadas, SSH de
  pé, health 200.

Uso geral do Docker na VM: socket só com `sudo` (`ubuntu` não está no grupo
`docker`) — inclusive é isso que evita surpresas.

### Security List (ingress) da OCI — defesa em profundidade

Com o host endurecido, o Security List da subnet foi enxugado no console OCI
para a mesma lista:

| Regra | Papel |
|---|---|
| TCP 22 de `0.0.0.0/0` | SSH |
| TCP 443 de `0.0.0.0/0` | HTTPS (nginx) |
| ICMP 3,4 de `0.0.0.0/0` | **manter** — "Fragmentation Needed", sem ele TLS pode congelar com pacotes grandes (PMTU) |
| ICMP 3 de `10.0.0.0/16` | interno da VNet |

A porta 80 foi **removida** do ingress (nunca chegou à VM de qualquer forma).
Validado pós-remoção: SSH e HTTPS no ar.
