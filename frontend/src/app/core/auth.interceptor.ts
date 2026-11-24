import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Auth } from './auth.service';
import { ApiUrl } from './api-url.service';

/**
 * Anexa o Bearer token somente a requisições que pertencem ao nosso backend.
 * Com base configurada: a URL da request tem de começar com `ApiUrl.base`.
 * Com base vazia (mesma origem): só URLs relativas podem levar o token —
 * qualquer URL absoluta é de domínio de terceiros (ex.: Community Dragon).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const base = inject(ApiUrl).base;
  const token = inject(Auth).token();
  const naNossaApi = base !== '' ? req.url.startsWith(base) : !/^https?:\/\//.test(req.url);
  if (token && naNossaApi) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
