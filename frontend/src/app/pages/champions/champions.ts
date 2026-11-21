import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { DataDragon, DDragonChampion } from '../../core/data-dragon.service';
import { Backend } from '../../core/backend.service';
import { Auth } from '../../core/auth.service';
import { of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

@Component({
  selector: 'app-champions',
  imports: [RouterLink],
  templateUrl: './champions.html',
  styleUrl: './champions.scss',
})
export class ChampionsPage {
  protected readonly dd = inject(DataDragon);
  protected readonly backend = inject(Backend);
  protected readonly auth = inject(Auth);
  private readonly router = inject(Router);

  protected readonly query = signal('');
  protected readonly rotationIds = signal<number[]>([]);

  protected readonly champions = toSignal<DDragonChampion[] | 'error' | null>(
    this.dd.listChampions().pipe(
      map((list) => [...list].sort((a, b) => a.name.localeCompare(b.name)) as DDragonChampion[]),
      catchError(() => of('error' as const)),
    ),
    { initialValue: null },
  );

  constructor() {
    // marca os campeões da rotação semanal (requisita via nosso backend)
    this.backend
      .rotation()
      .pipe(catchError(() => of({ freeChampionIds: [] as number[] })))
      .subscribe((r) => this.rotationIds.set(r.freeChampionIds ?? []));
  }

  protected readonly filtered = computed<DDragonChampion[] | null>(() => {
    const list = this.champions();
    if (!Array.isArray(list)) return null;
    const q = this.query().trim().toLowerCase();
    if (!q) return list;
    return list.filter(
      (c) => c.name.toLowerCase().includes(q) || c.title.toLowerCase().includes(q),
    );
  });

  protected onSearch(value: string): void {
    this.query.set(value);
  }

  protected isFreeToPlay(c: DDragonChampion): boolean {
    return this.rotationIds().includes(Number(c.key));
  }

  protected isFavorite(c: DDragonChampion): boolean {
    return this.backend.isFavorite(c.id);
  }

  protected toggleFavorite(c: DDragonChampion): void {
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    const action = this.isFavorite(c)
      ? this.backend.removeFavorite(c.id)
      : this.backend.addFavorite(c.id, c.name, c.title);
    action.catch((e) => alert(e?.error?.error ?? e.message));
  }
}
