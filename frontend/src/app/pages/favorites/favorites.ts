import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Auth } from '../../core/auth.service';
import { ApiUrl } from '../../core/api-url.service';
import { DataDragon } from '../../core/data-dragon.service';
import { Backend, CfFavorite } from '../../core/backend.service';
import { messageOf } from '../../core/api-error';

@Component({
  selector: 'app-favorites',
  imports: [RouterLink],
  templateUrl: './favorites.html',
  styleUrl: './favorites.scss',
})
export class FavoritesPage {
  protected readonly dd = inject(DataDragon);
  protected readonly backend = inject(Backend);
  protected readonly auth = inject(Auth);
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiUrl);

  protected readonly favorites = signal<CfFavorite[] | null>(null);
  protected readonly busy = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    if (!this.auth.isLoggedIn()) {
      this.favorites.set([]);
      return;
    }
    this.http.get<CfFavorite[]>(`${this.api.base}/api/favorites`).subscribe({
      next: (list) => this.favorites.set(list),
      error: () => this.favorites.set([]),
    });
  }

  protected async remove(f: CfFavorite): Promise<void> {
    this.busy.set(f.championId);
    try {
      await this.backend.removeFavorite(f.championId);
      this.load();
    } catch (e: unknown) {
      alert(messageOf(e));
    } finally {
      this.busy.set(null);
    }
  }
}
