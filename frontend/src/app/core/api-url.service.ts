import { Injectable, signal } from '@angular/core';

/**
 * Config em tempo de execução, carregada de /config.json na raiz do deploy.
 * Assim o MESMO build funciona em localhost (proxy não necessário) e no
 * GitHub Pages — só muda o config.json publicado.
 */
@Injectable({ providedIn: 'root' })
export class ApiUrl {
  /** URL base do backend (sem barra final). Vazio = mesma origem. */
  private readonly _base = signal('');

  readonly url = this._base.asReadonly();

  get base(): string {
    return this._base();
  }

  async load(): Promise<void> {
    try {
      const res = await fetch('config.json');
      if (res.ok) {
        const cfg = (await res.json()) as { apiUrl?: string };
        this._base.set((cfg.apiUrl ?? '').replace(/\/+$/, ''));
      }
    } catch {
      // usa a mesma origem
    }
  }
}
