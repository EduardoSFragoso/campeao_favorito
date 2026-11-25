import { WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Backend } from '../../core/backend.service';
import { Auth } from '../../core/auth.service';
import { AuthPage } from './auth';

const MSG_CONFIRMACAO =
  'Se este e-mail for novo, enviamos um link de confirmação. Confira a caixa de entrada.';

/** Acessa membros protected da página (só o template os usa em produção). */
function interna(p: AuthPage): {
  mode: WritableSignal<'login' | 'register'>;
  info(): string | null;
  error(): string | null;
  email: string;
  password: string;
  displayName: string;
  submit(): Promise<void>;
} {
  return p as unknown as {
    mode: WritableSignal<'login' | 'register'>;
    info(): string | null;
    error(): string | null;
    email: string;
    password: string;
    displayName: string;
    submit(): Promise<void>;
  };
}

describe('AuthPage — cadastro não loga mais, orienta a verificar o e-mail', () => {
  let auth: { register: ReturnType<typeof vi.fn>; login: ReturnType<typeof vi.fn> };
  let backend: { refreshFavorites: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn>; url: string };

  beforeEach(() => {
    auth = { register: vi.fn(), login: vi.fn() };
    backend = { refreshFavorites: vi.fn() };
    router = { navigate: vi.fn().mockResolvedValue(true), url: '/login' };
    TestBed.configureTestingModule({
      providers: [
        { provide: Auth, useValue: auth },
        { provide: Backend, useValue: backend },
        { provide: Router, useValue: router },
      ],
    });
  });

  afterEach(() => {
    localStorage.removeItem('cf_auth');
  });

  it('após cadastrar: mostra a mensagem de confirmação, volta ao login e NÃO navega', async () => {
    auth.register.mockResolvedValue(MSG_CONFIRMACAO);
    const f = TestBed.createComponent(AuthPage);
    const p = interna(f.componentInstance);
    p.mode.set('register');
    p.email = 'ana@teste.dev';
    p.password = 'senhaforte1';
    p.displayName = 'Ana';
    await p.submit();

    expect(p.info()).toBe(MSG_CONFIRMACAO);
    expect(p.mode()).toBe('login');
    expect(router.navigate).not.toHaveBeenCalled();
    expect(backend.refreshFavorites).not.toHaveBeenCalled();
  });

  it('login bloqueado por e-mail não confirmado (403): a mensagem do corpo aparece', async () => {
    auth.login.mockRejectedValue({ error: { error: 'Confirme o e-mail antes de entrar.' } });
    const f = TestBed.createComponent(AuthPage);
    const p = interna(f.componentInstance);
    p.email = 'ana@teste.dev';
    p.password = 'senhaforte1';
    await p.submit();

    expect(p.error()).toBe('Confirme o e-mail antes de entrar.');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
