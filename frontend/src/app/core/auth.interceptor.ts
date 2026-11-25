import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';

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

/** 401/403 da nossa API num endpoint que NÃO é de sessão pública. */
const rotaDeSessao = (url: string) =>
  url.endsWith('/api/auth/login') || url.endsWith('/api/auth/register') || url.endsWith('/api/auth/refresh');

/**
 * Tempestade de 401s (uma página dispara N chamadas): UMA renovação única em voo — todos
 * esperam a mesma promessa e repetem a requisição com o token novo. Sem isto, o 2º 401 já
 * reutilizaria um refresh ROTACIONADO e o backend entenderia roubo (derruba a sessão inteira).
 */
let renovacaoEmAndamento: Promise<boolean> | null = null;

function renovarUmaVez(auth: Auth): Promise<boolean> {
  renovacaoEmAndamento ??= auth.renovar().finally(() => {
    renovacaoEmAndamento = null;
  });
  return renovacaoEmAndamento;
}

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
   * Falha de sessão: 401 (JwtAuthFilter) ou 403 (rota protegida sem token —
   * RotasProtegidasSemTokenTest) da NOSSA API, FORA de login/register (credencial errada
   * tratada pela própria página, com refresh a rota /refresh em si) e fora de hosts
   * externos. 500/erros de rede não são sessão. Sem laço: já em /login, não navega de novo
   * — e a /refresh em si não dispara nova renovação.
   */
  const encerrarSessao = (erro: unknown) => {
    // limpeza SEMPRE (token morto não pode sobrar no localStorage); a navegação
    // só em páginas que não sejam o próprio /login, para não laçar
    auth.logout();
    if (router.url !== '/login') {
      router.navigateByUrl('/login');
    }
    return throwError(() => erro);
  };

  return next(req).pipe(
    catchError((err) => {
      const falhaDeSessao =
        err instanceof HttpErrorResponse &&
        naNossaApi &&
        (err.status === 401 || err.status === 403) &&
        !rotaDeSessao(req.url);
      if (!falhaDeSessao) {
        return throwError(() => err);
      }

      /** Sessão longa (opção B): 1 renovação em silêncio e repete com o token novo. */
      const redespacharComTokenNovo = () => {
        const tokenNovo = auth.token();
        const pedido = tokenNovo ? req.clone({ setHeaders: { Authorization: `Bearer ${tokenNovo}` } }) : req;
        return next(pedido);
      };

      return from(renovarUmaVez(auth)).pipe(
        switchMap((renovou) => (renovou ? redespacharComTokenNovo() : encerrarSessao(err))),
      );
    }),
  );
};
