import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Auth } from '../../core/auth.service';
import { Backend } from '../../core/backend.service';
import { messageOf } from '../../core/api-error';

@Component({
  selector: 'app-auth',
  imports: [FormsModule],
  templateUrl: './auth.html',
  styleUrl: './auth.scss',
})
export class AuthPage {
  protected readonly auth = inject(Auth);
  protected readonly backend = inject(Backend);
  private readonly router = inject(Router);

  protected mode = signal<'login' | 'register'>('login');

  protected email = '';
  protected password = '';
  protected displayName = '';

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected switchMode(): void {
    this.mode.set(this.mode() === 'login' ? 'register' : 'login');
    this.error.set(null);
  }

  protected async submit(): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      if (this.mode() === 'login') {
        await this.auth.login(this.email, this.password);
      } else {
        await this.auth.register(this.email, this.password, this.displayName);
      }
      this.backend.refreshFavorites();
      this.router.navigate(['/']);
    } catch (e: unknown) {
      this.error.set(messageOf(e, 'Falha na autenticação.'));
    } finally {
      this.busy.set(false);
    }
  }
}
