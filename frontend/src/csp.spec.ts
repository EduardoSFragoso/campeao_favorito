import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * A CSP é <meta http-equiv="Content-Security-Policy"> no index.html — o GitHub Pages não
 * deixa configurar header HTTP. Regras:
 *  - connect-src: 'self' + TODAS as origens https realmente atingidas por HttpClient/fetch
 *    (a origem do backend de produção = a mesma do config.json publicado pelo deploy) e as
 *    CDNs de dados de LoL — elas também servem JSON (versões, cromas) por XHR.
 *  - Data Dragon e Community Dragon também entram no img-src (splashes e ícones).
 *  - scripts só da própria origem: o CSS crítico do Angular é inline no build de produção —
 *    inlineCritical:false (a alternativa seriam hashes/nonce, que <meta> não suporta).
 *  - estilos de componente do Angular são injetados como <style> em runtime, o que exige
 *    'unsafe-inline' em style-src (risco baixo: não há input de usuário renderizado como
 *    style). frame-ancestors não funciona em <meta>.
 */
function extrairCsp(caminho = 'src/index.html'): string {
  const html = readFileSync(resolve(process.cwd(), caminho), 'utf-8');
  const meta = html.match(/<meta\s+http-equiv="Content-Security-Policy"\s+content="([^"]+)"/);
  expect(meta, 'index.html precisa da meta tag de CSP').not.toBeNull();
  return meta![1];
}

function diretivaDo(csp: string, nome: string): string[] | null {
  const p = csp
    .split(';')
    .map((d) => d.trim())
    .find((d) => d.startsWith(nome + ' '));
  return p ? p.slice(nome.length + 1).split(/\s+/) : null;
}

/** .ts do app, fora de specs — os literais https:// somente alimentam HttpClient/fetch. */
function arquivosTs(dir: string): string[] {
  const saida: string[] = [];
  for (const entrada of readdirSync(dir)) {
    const atual = join(dir, entrada);
    if (statSync(atual).isDirectory()) {
      saida.push(...arquivosTs(atual));
    } else if (entrada.endsWith('.ts') && !entrada.endsWith('.spec.ts')) {
      saida.push(atual);
    }
  }
  return saida;
}

/** Origens https:// literais usadas em frontend/src/app (HttpClient e fetch). */
function origensHttpsUsadasNoApp(): string[] {
  const origens = new Set<string>();
  for (const arquivo of arquivosTs('src/app')) {
    for (const captura of readFileSync(arquivo, 'utf-8').matchAll(/https:\/\/[^\s'"`)\]}]+/g)) {
      origens.add(new URL(captura[0]).origin);
    }
  }
  return [...origens].sort();
}

describe('CSP do index.html (fonte)', () => {
  const csp = extrairCsp();
  const cdnsDeImagemEDados = ['https://ddragon.leagueoflegends.com', 'https://raw.communitydragon.org'];

  it('não permite scripts fora da própria origem', () => {
    expect(diretivaDo(csp, 'script-src')).toEqual(["'self'"]);
  });

  it('permite imagens das CDNs realmente usadas (Data Dragon e Community Dragon)', () => {
    const img = diretivaDo(csp, 'img-src');
    expect(img).not.toBeNull();
    for (const cdn of cdnsDeImagemEDados) {
      expect(img, `img-src precisa permitir ${cdn}`).toContain(cdn);
    }
    expect(img).toContain("'self'");
    expect(img).toContain('data:');
  });

  it('connect-src: própria origem + CDNs de dados + a origem REAL da API', () => {
    const connect = diretivaDo(csp, 'connect-src');
    expect(connect).not.toBeNull();
    expect(connect).toContain("'self'");
    // as CDNs também servem JSON por XHR (versões do jogo, detalhes de itens/cromas)
    for (const cdn of cdnsDeImagemEDados) {
      expect(connect, `connect-src precisa permitir ${cdn}`).toContain(cdn);
    }
    // a origem real do backend (a mesma publicada no config.json do deploy) — e nada de
    // origem de exemplo que o site não usa
    expect(connect, 'connect-src precisa da origem da API de produção').toContain(
      'https://147-15-57-6.sslip.io',
    );
    expect(
      connect,
      'o código e o config.json não usam api.campeao-favorito.dev — não pode ficar na CSP',
    ).not.toContain('https://api.campeao-favorito.dev');
  });

  it('TODA origem https usada em HttpClient/fetch no app já está liberada no connect-src', () => {
    const connect = diretivaDo(csp, 'connect-src') ?? [];
    const origens = origensHttpsUsadasNoApp();
    expect(
      origens.length,
      'a varredura precisa achar as origens (senão o teste é vazio)',
    ).toBeGreaterThanOrEqual(2);
    for (const origem of origens) {
      expect(connect, `connect-src faltando a origem ${origem}`).toContain(origem);
    }
  });

  it('fecha o resto que a página não usa: object, font e formulário', () => {
    expect(diretivaDo(csp, 'object-src')).toEqual(["'none'"]);
    expect(diretivaDo(csp, 'form-action')).toEqual(["'self'"]);
    expect(diretivaDo(csp, 'base-uri')).toEqual(["'self'"]);
    const font = diretivaDo(csp, 'font-src');
    expect(font).not.toBeNull();
    expect(font).toContain("'self'");
    // estilos de componente do Angular são <style> em runtime
    const style = diretivaDo(csp, 'style-src');
    expect(style).toContain("'self'");
    expect(style).toContain("'unsafe-inline'");
  });
});
