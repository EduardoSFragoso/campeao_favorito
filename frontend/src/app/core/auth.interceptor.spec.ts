import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiUrl } from './api-url.service';
import { authInterceptor } from './auth.interceptor';

/** URL da CDN Community Dragon (cromas) — nunca pode receber o Bearer. */
const URL_CD_DRAGON =
  'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champions/266.json';

/** Base fictícia configurável via config.json stubado. */
const BASE_API = 'https://api.campeao-favorito.dev';

/** JWT de mentira, formato válido só o suficiente para o restore do Auth aceitar. */
function tokenFalso(): string {
  const header = btoa(JSON.stringify({ alg: 'none' }));
  // exp distante no futuro (ms desde epoch)
  const payload = btoa(JSON.stringify({ exp: 4102444800 }));
  return `${header}.${payload}.assinatura`;
}

describe('authInterceptor — Bearer anexado só quando a URL está na base da API', () => {
  let client: HttpClient;
  let http: HttpTestingController;

  async function montarComBase(config: { apiUrl?: string }): Promise<void> {
    localStorage.setItem(
      'cf_auth',
      JSON.stringify({
        token: tokenFalso(),
        user: { id: 1, email: 'teste@teste.dev', displayName: 'Teste' },
      }),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve(config) } as Response)),
    );
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    await TestBed.inject(ApiUrl).load();
  }

  afterEach(() => {
    localStorage.removeItem('cf_auth');
    vi.unstubAllGlobals();
    http.verify();
  });

  it('anexa Bearer à requisição na base da API (base configurada)', async () => {
    await montarComBase({ apiUrl: BASE_API });
    client.get(`${BASE_API}/api/riot/masteries`).subscribe();
    const req = http.expectOne(`${BASE_API}/api/riot/masteries`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer ' + tokenFalso());
    req.flush({});
  });

  it('não anexa Bearer à Community Dragon (URL fora da base)', async () => {
    await montarComBase({ apiUrl: BASE_API });
    client.get(URL_CD_DRAGON).subscribe();
    const req = http.expectOne(URL_CD_DRAGON);
    expect(req.request.headers.get('Authorization')).toBe(null);
    req.flush({});
  });

  it('anexa Bearer a URL relativa quando a base é vazia (mesma origem)', async () => {
    await montarComBase({});
    client.get('/api/auth/login').subscribe();
    const req = http.expectOne('/api/auth/login');
    expect(req.request.headers.get('Authorization')).toBe('Bearer ' + tokenFalso());
    req.flush({});
  });

  it('não anexa Bearer a URL absoluta externa quando a base é vazia', async () => {
    await montarComBase({});
    client.get(URL_CD_DRAGON).subscribe();
    const req = http.expectOne(URL_CD_DRAGON);
    expect(req.request.headers.get('Authorization')).toBe(null);
    req.flush({});
  });

  it('não anexa Bearer quando não há token', async () => {
    await montarComBase({ apiUrl: BASE_API });
    localStorage.removeItem('cf_auth');
    client.get(`${BASE_API}/api/auth/register`).subscribe();
    const req = http.expectOne(`${BASE_API}/api/auth/register`);
    expect(req.request.headers.get('Authorization')).toBe(null);
    req.flush({});
  });
});
