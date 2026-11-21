import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { ApiUrl } from './api-url.service';
import { Auth } from './auth.service';

/** Favorito conforme a tabela do Neon (via backend). */
export interface CfFavorite {
  id: number;
  userId: number;
  championId: string;
  championName: string;
  title?: string;
  createdAt: string;
}

/** Perfil agregado do invocador (Account + Summoner + League + Mastery da Riot). */
export interface SummonerProfile {
  puuid: string;
  gameName: string;
  tagLine: string;
  name: string;
  profileIconId: number;
  summonerLevel: number;
  ranked: {
    queueType: string;
    tier: string;
    rank: string;
    leaguePoints: number;
    wins: number;
    losses: number;
    winratePercent: number;
    games: number;
  }[];
  topMasteries: {
    championId: number;
    championLevel: number;
    championPoints: number;
    chestGranted: boolean;
  }[];
}

@Injectable({ providedIn: 'root' })
export class Backend {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiUrl);
  private readonly auth = inject(Auth);

  /** Ids dos campeões favoritados pelo usuário logado. */
  private readonly _favIds = signal<string[]>([]);
  readonly favIds = this._favIds.asReadonly();

  constructor() {
    this.refreshFavorites();
  }

  isFavorite(championId: string): boolean {
    return this._favIds().includes(championId);
  }

  /** Só carrega do servidor quando há sessão; zera ao sair/logar. */
  refreshFavorites(): void {
    if (!this.auth.isLoggedIn()) {
      this._favIds.set([]);
      return;
    }
    this.http.get<CfFavorite[]>(`${this.api.base}/api/favorites`).subscribe({
      next: (list) => this._favIds.set(list.map((f) => f.championId)),
      error: () => this._favIds.set([]),
    });
  }

  /**
   * Atualização otimista: o coração vira no clique (a lista de ids é ajustada
   * antes da resposta), o POST confirma em background e reverte em caso de erro.
   */
  async addFavorite(championId: string, championName: string, title?: string): Promise<void> {
    if (!this.auth.isLoggedIn()) {
      throw new Error('Faça login para favoritar.');
    }
    if (!this._favIds().includes(championId)) {
      this._favIds.update((ids) => [...ids, championId]);
    }
    try {
      await firstValueFrom(
        this.http.post<CfFavorite>(`${this.api.base}/api/favorites`, {
          championId,
          championName,
          title: title ?? '',
        }),
      );
    } catch (e) {
      this._favIds.update((ids) => ids.filter((id) => id !== championId));
      throw e;
    }
  }

  /** Mesma estratégia otimista, revertendo a remoção se o DELETE falhar. */
  async removeFavorite(championId: string): Promise<void> {
    const antes = this._favIds();
    this._favIds.update((ids) => ids.filter((id) => id !== championId));
    try {
      await firstValueFrom(this.http.delete(`${this.api.base}/api/favorites/${championId}`));
    } catch (e) {
      this._favIds.set(antes);
      throw e;
    }
  }

  summoner(gameName: string, tagLine: string, platform: string) {
    return this.http.get<SummonerProfile>(`${this.api.base}/api/riot/summoner`, {
      params: { gameName, tagLine, platform },
    });
  }

  rotation() {
    return this.http.get<{ freeChampionIds: number[]; maxNewPlayerLevel: number }>(
      `${this.api.base}/api/riot/rotation`,
    );
  }
}
