import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiUrl } from './api-url.service';
import { Auth } from './auth.service';

/** Base fictícia configurável via config.json stubado. */
const BASE_API = 'https://api.campeao-favorito.dev';

/** Resposta do cadastro (anti-enumeração): mensagem genérica, sem token nem dados da conta. */
const CADASTRO_RESPOSTA = {
  message: 'Se este e-mail for novo, enviamos um link de confirmação. Confira a caixa de entrada.',
};

describe('Auth — cadastro sem login automático (verificação de e-mail)', () => {
  let service: Auth;
  let http: HttpTestingController;

  async function montarComBase(): Promise<void> {
    localStorage.removeItem('cf_auth');
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({ ok: true, json: () => Promise.resolve({ apiUrl: BASE_API }) } as Response),
      ),
    );
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    await TestBed.inject(ApiUrl).load();
    service = TestBed.inject(Auth);
  }

  afterEach(() => {
    localStorage.removeItem('cf_auth');
    vi.unstubAllGlobals();
    http.verify();
  });

  it('register NÃO cria sessão: nenhum token volta na resposta e o usuário segue deslogado', async () => {
    await montarComBase();

    const promise = service.register('ana@teste.dev', 'senhaforte1', 'Ana');
    const req = http.expectOne(`${BASE_API}/api/auth/register`);
    expect(req.request.method).toBe('POST');
    req.flush(CADASTRO_RESPOSTA);
    await promise;

    expect(service.token()).toBeNull();
    expect(service.user()).toBeNull();
    expect(service.isLoggedIn()).toBe(false);
    expect(localStorage.getItem('cf_auth')).toBeNull();
  });

  it('register devolve a mensagem de orientação do cadastro', async () => {
    await montarComBase();

    const promise = service.register('ana@teste.dev', 'senhaforte1', 'Ana');
    http.expectOne(`${BASE_API}/api/auth/register`).flush(CADASTRO_RESPOSTA);
    const msg = await promise;

    expect(msg).toBe(CADASTRO_RESPOSTA.message);
  });

  it('verificar(token) faz GET /api/auth/verificar?token= e devolve a mensagem', async () => {
    await montarComBase();

    const promise = service.verificar('abc123');
    const req = http.expectOne(`${BASE_API}/api/auth/verificar?token=abc123`);
    expect(req.request.method).toBe('GET');
    req.flush({ message: 'E-mail confirmado — já pode entrar.' });
    const msg = await promise;

    expect(msg).toBe('E-mail confirmado — já pode entrar.');
  });

  it('login devolve e ARMAZENA token e refreshToken (sessão longa, opção B)', async () => {
    await montarComBase();

    const promise = service.login('ana@teste.dev', 'senhaforte1');
    http.expectOne(`${BASE_API}/api/auth/login`).flush({
      token: 'jwt-1',
      refreshToken: 'ref-1',
      user: { id: 7, email: 'ana@teste.dev', displayName: 'Ana' },
    });
    await promise;

    expect(service.token()).toBe('jwt-1');
    expect(service.refreshToken()).toBe('ref-1');
    expect(JSON.parse(localStorage.getItem('cf_auth') as string).refreshToken).toBe('ref-1');
  });

  it('renovar() troca o par no /api/auth/refresh e devolve true', async () => {
    await montarComBase();
    const promise = service.login('ana@teste.dev', 'senhaforte1');
    http.expectOne(`${BASE_API}/api/auth/login`).flush({
      token: 'jwt-1',
      refreshToken: 'ref-1',
      user: { id: 7, email: 'ana@teste.dev', displayName: 'Ana' },
    });
    await promise;

    const renovacao = service.renovar();
    const req = http.expectOne(`${BASE_API}/api/auth/refresh`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refreshToken: 'ref-1' });
    req.flush({
      token: 'jwt-2',
      refreshToken: 'ref-2',
      user: { id: 7, email: 'ana@teste.dev', displayName: 'Ana' },
    });

    await expect(renovacao).resolves.toBe(true);
    expect(service.token()).toBe('jwt-2');
    expect(service.refreshToken()).toBe('ref-2');
  });

  it('renovar() sem refresh armazenado devolve false e NÃO chama o servidor', async () => {
    await montarComBase();
    // sessionStorage vazio: nenhum login nesta bateria (cf_auth removido no montarComBase)
    await expect(service.renovar()).resolves.toBe(false);
    http.verify(); // nenhuma chamada pendente além da de config
  });

  it('logout com sessão revoga no servidor por fetch (fora do interceptor) e limpa o local', async () => {
    await montarComBase();
    const fetchMock = vi.fn(() => Promise.resolve({ ok: true } as Response));
    vi.stubGlobal('fetch', fetchMock);
    const promise = service.login('ana@teste.dev', 'senhaforte1');
    http
      .expectOne(`${BASE_API}/api/auth/login`)
      .flush({ token: 'jwt-antigo', user: { id: 7, email: 'ana@teste.dev', displayName: 'Ana' } });
    await promise;

    service.logout();

    expect(service.token()).toBeNull();
    expect(service.user()).toBeNull();
    expect(service.isLoggedIn()).toBe(false);
    expect(localStorage.getItem('cf_auth')).toBeNull();
    // um chamado só, com o token que a sessão tinha (inclusive no 401 do interceptor)
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${BASE_API}/api/auth/logout`);
    expect(init.method).toBe('POST');
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer jwt-antigo');
  });

  it('logout sem sessão não faz chamada nenhuma', async () => {
    await montarComBase();
    const fetchMock = vi.fn(() => Promise.resolve({ ok: true } as Response));
    vi.stubGlobal('fetch', fetchMock);

    service.logout();

    expect(fetchMock).not.toHaveBeenCalled();
  });
});
