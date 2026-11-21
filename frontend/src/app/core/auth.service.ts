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

  async register(email: string, password: string, displayName: string): Promise<void> {
    const res = await firstValueFrom(
      this.http.post<AuthResponse>(`${this.api.base}/api/auth/register`, {
        email,
        password,
        displayName,
      }),
    );
    this.setSession(res);
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
