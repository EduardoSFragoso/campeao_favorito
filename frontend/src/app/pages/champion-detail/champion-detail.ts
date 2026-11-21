import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { DataDragon, ChampDetail, isRealSkin } from '../../core/data-dragon.service';
import { Backend } from '../../core/backend.service';
import { Auth } from '../../core/auth.service';
import { messageOf } from '../../core/api-error';

@Component({
  selector: 'app-champion-detail',
  imports: [RouterLink],
  templateUrl: './champion-detail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './champion-detail.scss',
})
export class ChampionDetailPage {
  protected readonly dd = inject(DataDragon);
  protected readonly backend = inject(Backend);
  protected readonly auth = inject(Auth);

  protected readonly data = toSignal<ChampDetail | null>(
    inject(ActivatedRoute).paramMap.pipe(
      switchMap((pm) => {
        const id = pm.get('id');
        return id ? this.dd.championDetail(id) : of(null);
      }),
      catchError(() => of(null)),
    ),
    { initialValue: null },
  );

  protected readonly activeSkin = signal(0);

  /** Skins renderizáveis (cromas não têm splash próprio e ficam de fora). */
  protected readonly realSkins = computed(() => this.data()?.skins.filter(isRealSkin) ?? []);

  protected readonly splashUrl = computed(() => {
    const c = this.data();
    return c ? this.dd.splash(c.id, this.activeSkin()) : '';
  });

  protected readonly isFavorite = computed(() => {
    const c = this.data();
    return c ? this.backend.isFavorite(c.id) : false;
  });

  protected toggleFavorite(): void {
    const c = this.data();
    if (!c) return;
    if (!this.auth.isLoggedIn()) {
      window.location.hash = '#/login';
      return;
    }
    const action = this.isFavorite()
      ? this.backend.removeFavorite(c.id)
      : this.backend.addFavorite(c.id, c.name, c.title);
    action.catch((e: unknown) => alert(messageOf(e)));
  }

  protected statBar(value: number): number {
    return Math.max(4, value * 10);
  }
}
