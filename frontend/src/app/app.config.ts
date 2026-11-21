import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideAppInitializer,
  inject,
} from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { ApiUrl } from './core/api-url.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Hash routing: 100% compatível com o GitHub Pages (sem 404 em refresh)
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor])),
    // Carrega config.json (URL do backend) antes de qualquer componente
    provideAppInitializer(() => inject(ApiUrl).load()),
  ],
};
