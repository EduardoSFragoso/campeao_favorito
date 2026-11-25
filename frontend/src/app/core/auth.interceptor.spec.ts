import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiUrl } from './api-url.service';
import { authInterceptor } from './auth.interceptor';
import { routes } from '../app.routes';
import { Auth } from './auth.service';

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

  it('não anexa Bearer a subdomínio malicioso que só PREFIXA a base (base.evil.com)', async () => {
    await montarComBase({ apiUrl: BASE_API });
    // startsWith(base) SEM a barra casaria este prefixo e vazaria o token
    client.get(`${BASE_API}.evil.com/api/riot/masteries`).subscribe();
    const req = http.expectOne(`${BASE_API}.evil.com/api/riot/masteries`);
    expect(req.request.headers.get('Authorization')).toBe(null);
    req.flush({});
  });

  it('não anexa Bearer a URL protocol-relative (//host) quando a base é vazia', async () => {
    await montarComBase({});
    // //host não é relativa: é outro domínio inteiro (http por herança da página)
    client.get('//api.evil.com/api/auth/register').subscribe();
    const req = http.expectOne('//api.evil.com/api/auth/register');
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

/**
 * Sessão expirada ou inválida: 401/403 vindo da NOSSA API (o backend responde 403 para rota
 * protegida sem token — registrado no RotasProtegidasSemTokenTest; 401 é o auth do JwtAuthFilter)
 * deve limpar token/user e levar ao /login. Exceções: /api/auth/login e /api/auth/register (o
 * 401/403 ali é credencial errada tratada pela própria página) e qualquer host EXTERNO. 500 e
 * erros de rede também ficam de fora — não são sessão. Sem laço: já em /login, nada é navegado
 * de novo, e os endpoints de sessão estão isentos.
 */
describe('authInterceptor — sessão expirada/inválida (401/403)', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: Auth;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  async function montar(opcoes?: { comRefresh?: boolean }): Promise<void> {
    localStorage.setItem(
      'cf_auth',
      JSON.stringify({
        token: tokenFalso(),
        ...(opcoes?.comRefresh ? { refreshToken: 'ref-1' } : {}),
        user: { id: 1, email: 'teste@teste.dev', displayName: 'Teste' },
      }),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({ ok: true, json: () => Promise.resolve({ apiUrl: BASE_API }) } as Response),
      ),
    );
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter(routes),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(Auth);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
    await TestBed.inject(ApiUrl).load();
  }

  afterEach(() => {
    localStorage.removeItem('cf_auth');
    vi.unstubAllGlobals();
    http.verify();
  });

  function falhaComStatus(url: string, status: 401 | 403 | 500): void {
    client.get(`${BASE_API}${url}`).subscribe({ error: () => undefined });
    const req = http.expectOne(`${BASE_API}${url}`);
    req.flush(
      { error: 'falhou' },
      { status, statusText: status === 403 ? 'Forbidden' : status === 401 ? 'Unauthorized' : 'Server Error' },
    );
  }

  it('401 na rota protegida limpa a sessão e leva ao /login', async () => {
    await montar();
    expect(auth.token()).not.toBeNull();

    falhaComStatus('/api/favorites', 401);

    // a saída passou por renovação (assíncrona) antes do logout — esperar
    await vi.waitFor(() => expect(auth.token()).toBeNull());
    expect(auth.user()).toBeNull();
    expect(navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('403 na rota protegida (status que o backend dá sem token) faz o mesmo', async () => {
    await montar();

    falhaComStatus('/api/riot/rotation', 403);

    await vi.waitFor(() => expect(auth.token()).toBeNull());
    expect(navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('401 do /api/auth/login não desloga nem navega (credencial errada é da página)', async () => {
    await montar();

    falhaComStatus('/api/auth/login', 401);

    expect(auth.token()).not.toBeNull();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('403 do /api/auth/register não desloga nem navega', async () => {
    await montar();

    falhaComStatus('/api/auth/register', 403);

    expect(auth.token()).not.toBeNull();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('401/403 de host EXTERNO não mexe na sessão', async () => {
    await montar();
    client.get(URL_CD_DRAGON).subscribe({ error: () => undefined });
    const req = http.expectOne(URL_CD_DRAGON);
    req.flush({ error: 'fora' }, { status: 403, statusText: 'Forbidden' });

    expect(auth.token()).not.toBeNull();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('500 da nossa API não desloga (não é sessão)', async () => {
    await montar();

    falhaComStatus('/api/favorites', 500);

    expect(auth.token()).not.toBeNull();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('sem laço: já em /login, um 401 não navega de novo', async () => {
    await montar();
    await TestBed.inject(Router).navigate(['/login']);
    navigateByUrl.mockClear();

    falhaComStatus('/api/favorites', 401);

    await vi.waitFor(() => expect(auth.token()).toBeNull());
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('401 protegido COM refreshToken renova em silêncio e repete a requisição com o token novo', async () => {
    await montar({ comRefresh: true });

    client.get(`${BASE_API}/api/favorites`).subscribe({ error: () => undefined });
    // 1) a falha na protegida…
    http
      .expectOne(`${BASE_API}/api/favorites`)
      .flush({ error: 'expirado' }, { status: 401, statusText: 'Unauthorized' });
    // 2) …dispara a renovação com o refresh armazenado…
    const refreshReq = http.expectOne(`${BASE_API}/api/auth/refresh`);
    expect(refreshReq.request.body).toEqual({ refreshToken: 'ref-1' });
    refreshReq.flush({
      token: 'jwt-2',
      refreshToken: 'ref-2',
      user: { id: 1, email: 'teste@teste.dev', displayName: 'Teste' },
    });
    // 3) …e a requisição original reaparece com o JWT novo — sem logout nem /login
    let repeticao!: import('@angular/common/http/testing').TestRequest;
    await vi.waitFor(() => {
      repeticao = http.expectOne(`${BASE_API}/api/favorites`);
    });
    repeticao.flush([]);
    await vi.waitFor(() => expect(auth.token()).toBe('jwt-2'));
    expect(auth.refreshToken()).toBe('ref-2');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('renovação que falha (401 no /refresh) encerra a sessão e leva ao /login', async () => {
    await montar({ comRefresh: true });

    client.get(`${BASE_API}/api/favorites`).subscribe({ error: () => undefined });
    http
      .expectOne(`${BASE_API}/api/favorites`)
      .flush({ error: 'expirado' }, { status: 401, statusText: 'Unauthorized' });
    http
      .expectOne(`${BASE_API}/api/auth/refresh`)
      .flush({ error: 'refresh revogado' }, { status: 401, statusText: 'Unauthorized' });

    await vi.waitFor(() => expect(auth.token()).toBeNull());
    expect(navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('tempestade de 401s em paralelo dispara UMA renovação só (compartilhada)', async () => {
    await montar({ comRefresh: true });

    const falhar = (url: string) => {
      client.get(`${BASE_API}${url}`).subscribe({ error: () => undefined });
      http.expectOne(`${BASE_API}${url}`).flush({}, { status: 401, statusText: 'Unauthorized' });
    };
    falhar('/api/favorites');
    falhar('/api/riot/masteries');

    // UMA chamada de refresh: a renovação é compartilhada entre as falhas
    const refreshReq = http.expectOne(`${BASE_API}/api/auth/refresh`);
    refreshReq.flush({
      token: 'jwt-2',
      refreshToken: 'ref-2',
      user: { id: 1, email: 'teste@teste.dev', displayName: 'Teste' },
    });

    // as duas repetições saem com o MESMO token novo — evidência de que ambas esperaram a mesma
    let repeticao1!: import('@angular/common/http/testing').TestRequest;
    let repeticao2!: import('@angular/common/http/testing').TestRequest;
    await vi.waitFor(() => {
      repeticao1 = http.expectOne(`${BASE_API}/api/favorites`);
    });
    await vi.waitFor(() => {
      repeticao2 = http.expectOne(`${BASE_API}/api/riot/masteries`);
    });
    repeticao1.flush([]);
    repeticao2.flush([]);
    expect(repeticao1.request.headers.get('Authorization')).toBe('Bearer jwt-2');
    expect(repeticao2.request.headers.get('Authorization')).toBe('Bearer jwt-2');
    expect(auth.token()).toBe('jwt-2');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
