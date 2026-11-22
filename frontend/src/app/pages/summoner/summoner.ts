import { DecimalPipe } from '@angular/common';
import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { Backend, SummonerProfile } from '../../core/backend.service';
import { DataDragon } from '../../core/data-dragon.service';
import { messageOf } from '../../core/api-error';
import { resolveSummonerQuery } from './tag-resolver';

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

    const q = resolveSummonerQuery(raw, this.platform);
    if (!q) return;
    const gameName = q.gameName;
    const tagLine = q.tagLine;
    if (q.platform) {
      // tag conhecida: deriva a plataforma e canonicaliza (BR→BR1, KR→KR…)
      this.platform = q.platform;
    }

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
