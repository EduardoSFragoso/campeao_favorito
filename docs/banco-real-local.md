# Postgres descartável local para a suíte de banco real

Os testes `CorridaBancoRealTest` e `ApiApplicationTests` ficam **skipped** sem a variável
`DB_URL` no ambiente — na CI eles rodam contra um serviço efêmero do job backend. Este
guia reproduz esse serviço em um container Docker local, descartável, para usar a mesma
rede de segurança fora da CI. Nada aqui toca na CI nem no Neon.

## Subir o container (mesma configuração da CI)

```bash
docker run --rm -d --name pg-corrida \
  -e POSTGRES_DB=champion_favorito \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=teste \
  -p 5432:5432 postgres:17
```

- `--rm` + a parada derruba **e apaga** o container; os dados morrem com ele (descartável mesmo).
- A porta 5432 precisa estar livre; se não estiver, troque a publicação (`-p 5433:5432`) e
  ajuste a `DB_URL` na porta correspondente.

## Rodar os testes de banco real

Em Git Bash (Windows) ou Linux/macOS:

```bash
cd backend
DB_URL='jdbc:postgresql://localhost:5432/champion_favorito' \
DB_USER=postgres DB_PASSWORD=teste \
./mvnw -B test -Dtest='CorridaBancoRealTest,ApiApplicationTests'
```

Sem essas variáveis, a suíte volta ao comportamento padrão: os testes de banco real
ficam skipped e o resto roda normalmente.

## Derrubar

```bash
docker stop pg-corrida
```

`--rm` faz a remoção automática — não sobra volume nem dado.
