import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';

import { Auth } from '../../core/auth.service';
import { VerificarPage } from './verificar';

/** Acessa membros protected da VerificarPage em teste (só o template os usa em produção). */
function interna(p: VerificarPage): {
  status(): 'verificando' | 'ok' | 'erro';
  mensagem(): string;
} {
  return p as unknown as { status(): 'verificando' | 'ok' | 'erro'; mensagem(): string };
}

describe('VerificarPage — confirmação de e-mail pelo link', () => {
  function criarComToken(token: string | null): void {
    TestBed.configureTestingModule({
      providers: [
        { provide: Auth, useValue: { verificar: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } },
        },
      ],
    });
  }

  it('sem token na URL: não chama a API e já mostra erro pedindo o link do e-mail', async () => {
    criarComToken(null);
    const auth = TestBed.inject(Auth) as unknown as { verificar: ReturnType<typeof vi.fn> };
    const page = TestBed.createComponent(VerificarPage).componentInstance;
    const p = interna(page);

    await vi.waitFor(() => expect(p.status()).toBe('erro'));
    expect(auth.verificar).not.toHaveBeenCalled();
    expect(p.mensagem()).toContain('Link inválido');
  });

  it('com token: chama Auth.verificar(token) e mostra a mensagem de sucesso', async () => {
    criarComToken('tok123');
    const auth = TestBed.inject(Auth) as unknown as { verificar: ReturnType<typeof vi.fn> };
    auth.verificar.mockResolvedValue('E-mail confirmado — já pode entrar.');
    const page = TestBed.createComponent(VerificarPage).componentInstance;
    const p = interna(page);

    await vi.waitFor(() => expect(p.status()).toBe('ok'));
    expect(auth.verificar).toHaveBeenCalledWith('tok123');
    expect(p.mensagem()).toBe('E-mail confirmado — já pode entrar.');
  });

  it('token inválido/expirado: status erro com a mensagem da API', async () => {
    criarComToken('velho');
    const auth = TestBed.inject(Auth) as unknown as { verificar: ReturnType<typeof vi.fn> };
    auth.verificar.mockRejectedValue({ error: { error: 'Link expirou. Peça um novo.' } });
    const page = TestBed.createComponent(VerificarPage).componentInstance;
    const p = interna(page);

    await vi.waitFor(() => expect(p.status()).toBe('erro'));
    expect(p.mensagem()).toBe('Link expirou. Peça um novo.');
  });
});
