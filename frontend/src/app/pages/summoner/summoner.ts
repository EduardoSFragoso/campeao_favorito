import { DecimalPipe } from '@angular/common';
import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { Backend, SummonerProfile } from '../../core/backend.service';
import { DataDragon } from '../../core/data-dragon.service';
import { messageOf } from '../../core/api-error';

const PLATFORMS: { id: string; label: string }[] = [
  { id: 'br1', label: 'BR (br1)' },
  { id: 'na1', label: 'NA (na1)' },
  { id: 'euw1', label: 'EU West (euw1)' },
  { id: 'eun1', label: 'EU Nordic (eun1)' },
  { id: 'kr', label: 'Coreia (kr)' },
  { id: 'jp1', label: 'Japão (jp1)' },
  { id: 'la1', label: 'LATAM Norte (la1)' },
  { id: 'la2', label: 'LATAM Sul (la2)' },
  { id: 'oc1', label: 'Oceania (oc1)' },
  { id: 'tr1', label: 'Turquia (tr1)' },
];

/** Tag de Riot ID padrão de cada plataforma (KR = "KR", EUW = "EUW" etc.). */
const DEFAULT_TAG: Record<string, string> = {
  br1: 'BR1',
  na1: 'NA1',
  euw1: 'EUW',
  eun1: 'EUNE',
  kr: 'KR',
  jp1: 'JP1',
  la1: 'LAN',
  la2: 'LAS',
  oc1: 'OCE',
  tr1: 'TR1',
};

/** Tag digitada → plataforma que serve Summoner/League/Mastery (com aliases comuns). */
const TAG_TO_PLATFORM: Record<string, string> = {
  BR: 'br1',
  BR1: 'br1',
  NA: 'na1',
  NA1: 'na1',
  EUW: 'euw1',
  EUW1: 'euw1',
  EUN: 'eun1',
  EUNE: 'eun1',
  EUN1: 'eun1',
  KR: 'kr',
  KR1: 'kr',
  JP: 'jp1',
  JP1: 'jp1',
  LAN: 'la1',
  LA1: 'la1',
  LAS: 'la2',
  LA2: 'la2',
  OCE: 'oc1',
  OC1: 'oc1',
  TR: 'tr1',
  TR1: 'tr1',
};

@Component({
  selector: 'app-summoner',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './summoner.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './summoner.scss',
})
export class SummonerPage {
  protected readonly dd = inject(DataDragon);
  protected readonly backend = inject(Backend);

  protected readonly platforms = PLATFORMS;
  protected platform = 'br1';
  protected query = 'Galuverior';

  protected readonly profile = signal<SummonerProfile | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  protected async search(): Promise<void> {
    const raw = this.query.trim();
    if (!raw) return;

    // "GameName#TAG" → tag explícita (e plataforma derivada); só nome → tag padrão do dropdown
    const hash = raw.lastIndexOf('#');
    let gameName = raw;
    let tagLine = DEFAULT_TAG[this.platform] ?? 'BR1';
    if (hash > 0 && hash < raw.length - 1) {
      gameName = raw.slice(0, hash).trim();
      const typed = raw
        .slice(hash + 1)
        .trim()
        .toUpperCase();
      const plat = TAG_TO_PLATFORM[typed];
      if (plat) {
        // tag conhecida: deriva a plataforma e canonicaliza (BR→BR1, KR→KR…)
        this.platform = plat;
        tagLine = DEFAULT_TAG[plat] ?? typed;
      } else {
        tagLine = typed;
      }
    }
    if (!gameName || !tagLine) return;

    this.profile.set(null);
    this.error.set(null);
    this.loading.set(true);
    try {
      // mapa de ícones e API do backend em paralelo — a 1ª busca da sessão
      // não fica refém do download do champion.json
      const [res] = await Promise.all([
        firstValueFrom(this.backend.summoner(gameName, tagLine, this.platform)),
        this.dd.ensureChampionMap(),
      ]);
      this.profile.set(res);
    } catch (e: unknown) {
      this.error.set(messageOf(e, 'Falha na busca.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected queueLabel(queue: string): string {
    if (queue.includes('SOLO')) return 'Ranqueada Solo/Duo';
    if (queue.includes('FLEX')) return 'Flex 5v5';
    return queue;
  }
}
