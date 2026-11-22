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
  protected gameName = 'Hide on bush';
  protected tagLine = 'KR1';

  protected readonly profile = signal<SummonerProfile | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  protected async search(): Promise<void> {
    this.profile.set(null);
    this.error.set(null);
    this.loading.set(true);
    try {
      await this.dd.loadVersion(); // garante ícones (profileicon/champion)
      await this.dd.ensureChampionMap(); // mapa key numérico → id para ícones de maestria
      const res = await firstValueFrom(this.backend.summoner(this.gameName, this.tagLine, this.platform));
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
