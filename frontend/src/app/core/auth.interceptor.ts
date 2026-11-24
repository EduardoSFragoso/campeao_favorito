import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
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
  const token = inject(Auth).token();
  const naNossaApi = base !== '' ? req.url.startsWith(base + '/') : /^\/(?!\/)/.test(req.url);
  if (token && naNossaApi) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
