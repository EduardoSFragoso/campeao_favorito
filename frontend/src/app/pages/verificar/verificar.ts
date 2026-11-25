import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { messageOf } from '../../core/api-error';
import { Auth } from '../../core/auth.service';

/**
 * Destino do link de confirmação de e-mail. Pública (a rota da API também é): carrega o token da
 * query, confirma via backend e aponta para o login. Sem reenvio automático — o reenvio manual
 * existe depois, na própria página (a desenvolver) e por rate limit no backend.
 */
@Component({
  selector: 'app-verificar',
  imports: [RouterLink],
  templateUrl: './verificar.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './verificar.scss',
})
export class VerificarPage {
  protected readonly status = signal<'verificando' | 'ok' | 'erro'>('verificando');
  protected readonly mensagem = signal('Confirmando seu e-mail…');

  constructor() {
    const token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';
    if (token === '') {
      this.status.set('erro');
      this.mensagem.set('Link inválido — use o link enviado ao seu e-mail.');
      return;
    }
    inject(Auth)
      .verificar(token)
      .then((msg) => {
        this.status.set('ok');
        this.mensagem.set(msg);
      })
      .catch((e: unknown) => {
        this.status.set('erro');
        this.mensagem.set(messageOf(e, 'Não foi possível confirmar o e-mail.'));
      });
  }
}
