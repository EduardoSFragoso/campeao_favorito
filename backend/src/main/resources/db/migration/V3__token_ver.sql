-- Versão da sessão: incrementada a cada logout; JWT antigo (claim "ver" defasado) deixa de
-- autenticar, pois o JwtAuthFilter compara o claim com o valor atual do banco. Sem lista de
-- sessões: um número por usuário revoga TODOS os tokens emitidos antes.
ALTER TABLE users ADD COLUMN token_ver SMALLINT NOT NULL DEFAULT 0;
