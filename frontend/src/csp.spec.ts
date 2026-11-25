import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * A CSP é <meta http-equiv="Content-Security-Policy"> no index.html — o GitHub Pages não
 * deixa configurar header HTTP. Regras: a origem da API entra no connect-src; Data Dragon
 * e Community Dragon entram no img-src (splashes e ícones); scripts só da própria origem;
 * estilos de componente do Angular são injetados como <style> em runtime, o que exige
 * 'unsafe-inline' em style-src (risco baixo: não há input de usuário renderizado como
 * style). frame-ancestors não funciona em <meta>.
 */
function extrairCsp(): string {
  const html = readFileSync(resolve(process.cwd(), 'src/index.html'), 'utf-8');
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

describe('CSP do index.html', () => {
  const csp = extrairCsp();
  const cdnsImagem = ['https://ddragon.leagueoflegends.com', 'https://raw.communitydragon.org'];

  it('não permite scripts fora da própria origem', () => {
    expect(diretivaDo(csp, 'script-src')).toEqual(["'self'"]);
  });

  it('permite imagens das CDNs realmente usadas (Data Dragon e Community Dragon)', () => {
    const img = diretivaDo(csp, 'img-src');
    expect(img).not.toBeNull();
    for (const cdn of cdnsImagem) {
      expect(img, `img-src precisa permitir ${cdn}`).toContain(cdn);
    }
    expect(img).toContain("'self'");
    expect(img).toContain('data:');
  });

  it('restringe chamadas de rede (connect-src) à própria origem e à origem da API', () => {
    const connect = diretivaDo(csp, 'connect-src');
    expect(connect).not.toBeNull();
    expect(connect).toContain("'self'");
    // a base da API é runtime (config.json), mas a de produção é fixa — deve estar na CSP
    expect(connect, 'connect-src precisa da origem da API').toContain('https://api.campeao-favorito.dev');
    // CDNs de imagem não servem para fetch/XHR
    for (const cdn of cdnsImagem) {
      expect(connect, `${cdn} não é origem de API`).not.toContain(cdn);
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
