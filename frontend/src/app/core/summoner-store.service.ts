import { Injectable, signal } from '@angular/core';
import { SummonerProfile } from './backend.service';

/** Entrada de maestria vinda de GET /api/riot/masteries. */
export interface MasteryEntry {
  championId: number;
  championLevel: number;
  championPoints: number;
  chestGranted: boolean;
}

/** Invocador cuja maestria está ativa no grid. */
export interface SummonerMasteryState {
  puuid: string;
  gameName: string;
  tagLine: string;
  platform: string;
  /** key numérico do campeão (ex.: "266") → nível de maestria. */
  levels: Record<string, number>;
}

/**
 * Guarda em sessionStorage o último invocador buscado na página de invocador,
 * para o grid exibir a maestria de cada campeão. Sesso-focused por escolha:
 * maestria é dado pessoal e os caches do backend expiram rápido.
 */
@Injectable({ providedIn: 'root' })
export class SummonerStore {
  private static readonly KEY = 'campeao-favorito.mastery-summoner';

  private readonly state = signal<SummonerMasteryState | null>(SummonerStore.load());
  readonly current = this.state.asReadonly();

  /** Nível de maestria do invocador ativo para o campeão (key numérico) ou null. */
  levelFor(championKey: string): number | null {
    return this.state()?.levels[championKey] ?? null;
  }

  /** Registra o invocador buscado com a maestria completa recém-buscada. */
  set(profile: SummonerProfile, masteries: MasteryEntry[], platform: string): void {
    const levels: Record<string, number> = {};
    for (const m of masteries) levels[String(m.championId)] = m.championLevel;
    const next: SummonerMasteryState = {
      puuid: profile.puuid,
      gameName: profile.gameName,
      tagLine: profile.tagLine,
      platform,
      levels,
    };
    try {
      sessionStorage.setItem(SummonerStore.KEY, JSON.stringify(next));
    } catch {
      // storage cheio/indisponível: segue só em memória
    }
    this.state.set(next);
  }

  clear(): void {
    try {
      sessionStorage.removeItem(SummonerStore.KEY);
    } catch {
      // storage indisponível: limpa só em memória
    }
    this.state.set(null);
  }

  private static load(): SummonerMasteryState | null {
    try {
      const raw = sessionStorage.getItem(SummonerStore.KEY);
      return raw ? (JSON.parse(raw) as SummonerMasteryState) : null;
    } catch {
      return null;
    }
  }
}
