import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Auth } from '../../core/auth.service';
import { Backend } from '../../core/backend.service';
import { messageOf } from '../../core/api-error';

@Component({
  selector: 'app-auth',
  imports: [FormsModule],
  templateUrl: './auth.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
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
  /** Mensagem de orientação pós-cadastro (verifique seu e-mail) exibida no modo login. */
  protected readonly info = signal<string | null>(null);

  protected switchMode(): void {
    this.mode.set(this.mode() === 'login' ? 'register' : 'login');
    this.error.set(null);
    this.info.set(null);
  }

  protected async submit(): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    try {
      if (this.mode() === 'login') {
        await this.auth.login(this.email, this.password);
        this.backend.refreshFavorites();
        this.router.navigate(['/']);
      } else {
        // cadastro não autentica (e-mail pendente de confirmação): mostra a orientação
        // e volta ao login, onde a confirmação pode ser usada
        this.info.set(await this.auth.register(this.email, this.password, this.displayName));
        this.mode.set('login');
      }
    } catch (e: unknown) {
      this.error.set(messageOf(e, 'Falha na autenticação.'));
    } finally {
      this.busy.set(false);
    }
  }
}
