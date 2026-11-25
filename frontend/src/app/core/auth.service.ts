import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { ApiUrl } from './api-url.service';

/** Conta logada (persistida no localStorage enquanto o token for válido). */
export interface CfUser {
  id: number;
  email: string;
  displayName: string;
}

interface AuthResponse {
  token: string;
  /** Presente a partir da sessão longa (opção B); responses antigos podem não trazê-lo. */
  refreshToken?: string;
  user: CfUser;
}

/** Resposta do cadastro (anti-enumeração): mensagem genérica, SEM token e SEM dados da conta. */
interface CadastroResponse {
  message: string;
}

interface MensagemResponse {
  message: string;
}

const STORAGE_KEY = 'cf_auth';

@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly _user = signal<CfUser | null>(restoreUser());
  private readonly _token = signal<string | null>(restoreToken());
  private readonly _refreshToken = signal<string | null>(restoreRefreshToken());

  readonly user = this._user.asReadonly();
  readonly token = this._token.asReadonly();
  readonly refreshToken = this._refreshToken.asReadonly();
  readonly isLoggedIn = computed(() => this._token() !== null);

  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiUrl);

  /**
   * Cadastra sem autenticar (o login só existe após a confirmação do e-mail) e devolve a mensagem
   * de orientação enviada pelo backend. NÃO cria sessão.
   */
  async register(email: string, password: string, displayName: string): Promise<string> {
    const res = await firstValueFrom(
      this.http.post<CadastroResponse>(`${this.api.base}/api/auth/register`, {
        email,
        password,
        displayName,
      }),
    );
    return res.message;
  }

  /** Confirma o e-mail pelo token do link e devolve a mensagem do backend. */
  async verificar(token: string): Promise<string> {
    const res = await firstValueFrom(
      this.http.get<MensagemResponse>(`${this.api.base}/api/auth/verificar`, {
        params: { token },
      }),
    );
    return res.message;
  }

  async login(email: string, password: string): Promise<void> {
    const res = await firstValueFrom(
      this.http.post<AuthResponse>(`${this.api.base}/api/auth/login`, { email, password }),
    );
    this.setSession(res);
  }

  logout(): void {
    const token = this._token();
    this._user.set(null);
    this._token.set(null);
    this._refreshToken.set(null);
    localStorage.removeItem(STORAGE_KEY);
    if (token) {
      // Revoga no servidor (POST /api/auth/logout → token_ver++ mata todo JWT antigo).
      // fetch FORA do HttpClient de propósito: o POST não passa pelo interceptor — um 401
      // daqui não pode disparar logout de novo (laço). Falha é ignorada: a sessão local já
      // acabou; se o servidor não ouviu, o próprio token morre em 24h.
      fetch(`${this.api.base}/api/auth/logout`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      }).catch(() => undefined);
    }
  }

  /**
   * Sessão longa (opção B): troca o refreshToken por um par NOVO (rotação no servidor). Usado
   * pelo interceptor quando uma rota protegida devolve 401 e antes do logout desperdiçar a
   * sessão. false = sessão irrecuperável (sem refresh armazenado, revogado ou expirado).
   */
  async renovar(): Promise<boolean> {
    const refresh = this._refreshToken();
    if (!refresh) {
      return false;
    }
    try {
      const res = await firstValueFrom(
        this.http.post<AuthResponse>(`${this.api.base}/api/auth/refresh`, {
          refreshToken: refresh,
        }),
      );
      this.setSession(res);
      return true;
    } catch {
      return false;
    }
  }

  private setSession(res: AuthResponse): void {
    this._user.set(res.user);
    this._token.set(res.token);
    this._refreshToken.set(res.refreshToken ?? null);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(res));
  }
}

function restoreUser(): CfUser | null {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    return stored?.user ?? null;
  } catch {
    return null;
  }
}

function restoreToken(): string | null {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    const token: string | null = stored?.token ?? null;
    if (!token) return null;
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 < Date.now() ? null : token;
  } catch {
    return null;
  }
}

function restoreRefreshToken(): string | null {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null');
    return stored?.refreshToken ?? null;
  } catch {
    return null;
  }
}
