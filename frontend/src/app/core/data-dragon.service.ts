import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, from, map, switchMap } from 'rxjs';

/**
 * Acesso ao Data Dragon / Community Dragon (CDNs estáticos da Riot,
 * sem chave de API). Versões mudam a cada patch.
 *
 * - champion.json  → lista de campeões (id, key, name, title, blurb, tags)
 * - champion/{id}.json → detalhe (lore, stats, spells, skins)
 */
@Injectable({ providedIn: 'root' })
export class DataDragon {
  private static readonly BASE = 'https://ddragon.leagueoflegends.com';

  private readonly _version = signal<string | null>(null);
  /** Versão atual do patch (ex.: "26.1.1"). */
  readonly version = this._version.asReadonly();

  /** Mapa key numérico (Riot API) → id do Data Dragon, ex.: 266 → "Aatrox". */
  private readonly keyToId = new Map<string, string>();

  private readonly http = inject(HttpClient);

  /** Carrega a versão do patch se ainda não estiver carregada. */
  async loadVersion(): Promise<void> {
    if (this._version()) return;
    const versions = await firstValueFrom(this.http.get<string[]>(`${DataDragon.BASE}/api/versions.json`));
    this._version.set(versions[0]);
  }

  /** Lista todos os campeões (pt_BR). */
  listChampions() {
    return from(this.loadVersion()).pipe(
      switchMap(() =>
        this.http.get<ChampionListResponse>(
          `${DataDragon.BASE}/cdn/${this._version()}/data/pt_BR/champion.json`,
        ),
      ),
      map((res) => {
        const champs = Object.values(res.data);
        champs.forEach((c) => this.keyToId.set(c.key, c.id));
        return champs;
      }),
    );
  }

  /** Detalhe completo de um campeão (pt_BR). */
  championDetail(id: string) {
    return from(this.loadVersion()).pipe(
      switchMap(() =>
        this.http.get<ChampionDetailResponse>(
          `${DataDragon.BASE}/cdn/${this._version()}/data/pt_BR/champion/${id}.json`,
        ),
      ),
      map((res) => Object.values(res.data)[0]),
    );
  }

  /** Rotação semanal via nosso backend (Riot API Champion-V3). */
  rotation(apiBase: string) {
    return this.http.get<{ freeChampionIds: number[] }>(`${apiBase}/api/riot/rotation`);
  }

  // ---------- URLs de imagens ----------

  /** Ícone quadrado (40x40 até 120x120 conforme _v). */
  squareIcon(championId: string): string {
    const v = this._version();
    return `${DataDragon.BASE}/cdn/${v}/img/champion/${championId}.png`;
  }

  /** Loading screen (retrato vertical, ótimo para o grid). */
  loading(championId: string, skinNum = 0): string {
    return `${DataDragon.BASE}/cdn/img/champion/loading/${championId}_${skinNum}.jpg`;
  }

  /** Splash art (paisagem, 1215x717+). */
  splash(championId: string, skinNum = 0): string {
    return `${DataDragon.BASE}/cdn/img/champion/splash/${championId}_${skinNum}.jpg`;
  }

  /** Ícone de perfil do invocador. */
  profileIcon(profileIconId: number): string {
    const v = this._version();
    return `${DataDragon.BASE}/cdn/${v}/img/profileicon/${profileIconId}.png`;
  }

  /** Ícone quadrado do campeão a partir do key numérico (maestria da Riot). */
  squareIconByKey(numericKey: number): string | null {
    const id = this.keyToId.get(String(numericKey));
    return id ? this.squareIcon(id) : null;
  }

  /** Ícone de habilidade (Q/W/E/R) ou do passivo. */
  spellIcon(imageFull: string): string {
    const v = this._version();
    return `${DataDragon.BASE}/cdn/${v}/img/spell/${imageFull}`;
  }

  passiveIcon(imageFull: string): string {
    const v = this._version();
    return `${DataDragon.BASE}/cdn/${v}/img/passive/${imageFull}`;
  }
}

// ---------- interfaces do payload do Data Dragon ----------

export interface DDragonChampion {
  id: string;
  key: string;
  name: string;
  title: string;
  blurb: string;
  tags: string[];
}

export interface ChampDetail extends DDragonChampion {
  lore: string;
  spells: { id: string; name: string; description: string; image: { full: string } }[];
  /**
   * Lista de skins do campeão.
   * - skin real → `chromas` indica se ela tem cromas disponíveis;
   * - croma → vem com `parentSkin` apontando para o `num` da skin base e
   *   NÃO possui splash próprio no CDN (compartilha o da skin base).
   */
  skins: { id: number; num: number; name: string; chromas: boolean; parentSkin?: number }[];
  info: { attack: number; defense: number; magic: number; difficulty: number };
}

/** Skin real do Data Dragon (cromas têm `parentSkin` e não têm arte própria). */
export function isRealSkin(skin: ChampDetail['skins'][number]): boolean {
  return skin.parentSkin === undefined;
}

interface ChampionListResponse {
  type: string;
  version: string;
  data: Record<string, DDragonChampion>;
}

interface ChampionDetailResponse {
  type: string;
  version: string;
  data: Record<string, ChampDetail>;
}
