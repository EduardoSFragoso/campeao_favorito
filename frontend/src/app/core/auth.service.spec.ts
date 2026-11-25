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
});
