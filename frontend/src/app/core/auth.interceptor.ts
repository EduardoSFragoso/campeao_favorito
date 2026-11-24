import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { Auth } from './auth.service';
import { ApiUrl } from './api-url.service';

/**
 * Anexa o Bearer token somente a requisições que pertencem ao nosso backend.
 * Com base configurada: a URL tem de começar com `ApiUrl.base` + "/" — startsWith na base nua
 * casaria prefixo de outro domínio (ex.: `base.evil.com` herda o prefixo da base).
 * Com base vazia (mesma origem): só caminhos relativos daqui podem levar o token — nem URL
 * absoluta externa (ex.: Community Dragon), nem `//host` (protocol-relative, herda o esquema
 * da página e aponta para domínio de terceiros).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const base = inject(ApiUrl).base;
  const auth = inject(Auth);
  const router = inject(Router);
  const token = auth.token();
  const naNossaApi = base !== '' ? req.url.startsWith(base + '/') : /^\/(?!\/)/.test(req.url);
  if (token && naNossaApi) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  /**
   * Sessão expirada/inválida: 401 (do JwtAuthFilter) ou 403 (o que o backend responde hoje para
   * rota protegida sem token — RotasProtegidasSemTokenTest) da NOSSA API encerra a sessão e leva
   * ao /login. Isentos: /api/auth/login e /api/auth/register (401/403 ali é credencial errada,
   * tratada pela própria página) e qualquer host externo (a CDN nunca muda a nossa sessão).
   * 500/erros de rede não são sessão. Sem laço: já em /login, não navega de novo — e os
   * endpoints de sessão estão isentos.
   */
  return next(req).pipe(
    catchError((err) => {
      if (err instanceof HttpErrorResponse && naNossaApi) {
        const rotaDeSessao = req.url.endsWith('/api/auth/login') || req.url.endsWith('/api/auth/register');
        if ((err.status === 401 || err.status === 403) && !rotaDeSessao) {
          // limpeza SEMPRE (token morto não pode sobrar no localStorage);
          // a navegação é que se evita em /login, para não laçar
          auth.logout();
          if (router.url !== '/login') {
            router.navigateByUrl('/login');
          }
        }
      }
      return throwError(() => err);
    }),
  );
};
