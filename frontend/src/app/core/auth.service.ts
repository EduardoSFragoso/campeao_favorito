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

  readonly user = this._user.asReadonly();
  readonly token = this._token.asReadonly();
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
    this._user.set(null);
    this._token.set(null);
    localStorage.removeItem(STORAGE_KEY);
  }

  private setSession(res: AuthResponse): void {
    this._user.set(res.user);
    this._token.set(res.token);
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
