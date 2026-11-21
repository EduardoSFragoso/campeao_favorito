import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { Auth } from './core/auth.service';
import { Backend } from './core/backend.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(Auth);
  protected readonly backend = inject(Backend);
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout();
    this.backend.refreshFavorites();
    this.router.navigate(['/']);
  }
}
