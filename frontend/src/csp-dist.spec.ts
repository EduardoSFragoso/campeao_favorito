import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * O quebra-cabeça do build: alguns bloqueios só aparecem (ou podem ser introduzidos) no HTML
 * FINAL de produção, gerado por `ng build`:
 *  - o CSS crítico do Angular (inlineCritical, ligado por padrão) injeta um <script> INLINE,
 *    que morre no CSP `script-src 'self'` — o garde atrás deste teste;
 *  - o index.html vai com o valor de dev da API; o deploy substitui pela origem real de
 *    ${{ vars.API_URL }} — aqui pede-se que o config.json PUBLICADO e a CSP apontem para a
 *    MESMA origem (deixar a `api.campeao-favorito.dev` na CSP com config.json apontando para
 *    outro host é exatamente o que quebrou o site em produção).
 *
 * Requisitos: rode `npx ng build` antes (a CI faz build ANTES de testar). Sem dist, a bateria
 * pula — e o build tem de ser de produção (defaultConfiguration já é production).
 */
const DIST = resolve(process.cwd(), 'dist', 'frontend', 'browser');

describe.skipIf(!existsSync(join(DIST, 'index.html')))('CSP do HTML gerado (dist)', () => {
  const html = readFileSync(join(DIST, 'index.html'), 'utf-8');
  const meta = html.match(/<meta\s+http-equiv="Content-Security-Policy"\s+content="([^"]+)"/);
  const csp = meta?.[1] ?? '';
  const configPublicada = JSON.parse(readFileSync(join(DIST, 'config.json'), 'utf-8')) as {
    apiUrl?: string;
  };

  function diretivaDo(nome: string): string[] {
    const p = csp
      .split(';')
      .map((d) => d.trim())
      .find((d) => d.startsWith(nome + ' '));
    expect(p, `dist: CSP sem a diretiva ${nome}`).toBeDefined();
    return p!.slice(nome.length + 1).split(/\s+/);
  }

  it('a origem da API no connect-src é a MESMA do config.json publicado', () => {
    const api = (configPublicada.apiUrl ?? '').replace(/\/+$/, '');
    const connect = diretivaDo('connect-src');
    // mesma origem no deploy sem backend: 'self' cobre
    expect(connect, `connect-src precisa da origem do config.json ${api}`).toContain(
      api !== '' ? api : "'self'",
    );
    expect(
      connect,
      'a CSP aponta para origem que o build não usa (é assim que o site quebra).',
    ).not.toContain('https://api.campeao-favorito.dev');
  });

  it('nenhum placeholder de API sobra sem substituição', () => {
    expect(html).not.toContain('__API_URL__');
  });

  it('nenhum <script> inline (sem src) e nenhum atributo on*= no HTML final', () => {
    const scriptsInline = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script[^>]*>/gi)]
      .map((m) => ({ attrs: m[1] ?? '', corpo: (m[2] ?? '').trim() }))
      .filter((s) => !/\bsrc\s*=/.test(s.attrs) && s.corpo !== '');
    expect(
      scriptsInline.map((s) => s.corpo.slice(0, 120)),
      'script inline no build de produção: desligue o inlineCritical (a meta CSP não ' +
        "suporta nonce/hash) — senão script-src 'self' mata o CSS crítico",
    ).toEqual([]);
    expect(
      html.match(/\son[a-z]+\s*=\s*["']/gi),
      'atributo on* inline no HTML final (bloqueado pela CSP)',
    ).toBeNull();
  });
});
