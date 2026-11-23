import { Component, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { DataDragon, DDragonChampion } from '../../core/data-dragon.service';
import { Backend } from '../../core/backend.service';
import { Auth } from '../../core/auth.service';
import { SummonerStore } from '../../core/summoner-store.service';
import { of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

@Component({
  selector: 'app-champions',
  imports: [RouterLink],
  templateUrl: './champions.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './champions.scss',
})
export class ChampionsPage {
  protected readonly dd = inject(DataDragon);
  protected readonly backend = inject(Backend);
  protected readonly auth = inject(Auth);
  /** Invocador ativo (último buscado) para os badges de maestria do grid. */
  protected readonly summonerStore = inject(SummonerStore);
  private readonly router = inject(Router);

  protected readonly query = signal('');
  protected readonly rotationIds = signal<number[]>([]);
  /** Classe selecionada no filtro (null = Todas). */
  protected readonly classe = signal<string | null>(null);
  /** Ordenação do grid. */
  protected readonly ordenacao = signal<'nome' | 'nomeDesc' | 'ataque' | 'defesa' | 'magia' | 'dificuldade'>(
    'nome',
  );

  /** Rótulos PT das classes do Data Dragon (chips de filtro). */
  private static readonly CLASSES_PT: Record<string, string> = {
    Assassin: 'Assassino',
    Fighter: 'Lutador',
    Mage: 'Mago',
    Marksman: 'Atirador',
    Support: 'Suporte',
    Tank: 'Tanque',
  };

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

  /** Classes presentes na lista (estável, na ordem canônica dos rótulos). */
  protected readonly classes = computed<string[]>(() => {
    const list = this.champions();
    if (!Array.isArray(list)) return [];
    const presentes = new Set(list.flatMap((c) => c.tags));
    return Object.keys(ChampionsPage.CLASSES_PT).filter((tag) => presentes.has(tag));
  });

  protected classeLabel(tag: string): string {
    return ChampionsPage.CLASSES_PT[tag] ?? tag;
  }

  protected readonly filtered = computed<DDragonChampion[] | null>(() => {
    const list = this.champions();
    if (!Array.isArray(list)) return null;
    const q = this.query().trim().toLowerCase();
    const classe = this.classe();
    let acc = list;
    if (classe) acc = acc.filter((c) => c.tags.includes(classe));
    if (q) {
      // busca apenas pelo nome do campeão (título/descricao não entram no match)
      acc = acc.filter((c) => {
        const n = c.name
          .toLowerCase()
          .normalize('NFD')
          .replace(/[\u0300-\u036f]/g, '');
        return n.includes(q.normalize('NFD').replace(/[\u0300-\u036f]/g, ''));
      });
    }
    return this.ordenar(acc, this.ordenacao());
  });

  /** Copia antes de ordenar — não muta a lista cacheada do serviço. */
  private ordenar(
    list: DDragonChampion[],
    ord: 'nome' | 'nomeDesc' | 'ataque' | 'defesa' | 'magia' | 'dificuldade',
  ): DDragonChampion[] {
    const copia = [...list];
    if (ord === 'nome' || ord === 'nomeDesc') {
      const dir = ord === 'nomeDesc' ? -1 : 1;
      return copia.sort((a, b) => dir * a.name.localeCompare(b.name));
    }
    const campo = {
      ataque: 'attack',
      defesa: 'defense',
      magia: 'magic',
      dificuldade: 'difficulty',
    }[ord] as keyof NonNullable<DDragonChampion['info']>;
    // stats em ordem desc; empate cai para a ordem alfabética
    return copia.sort(
      (a, b) => (b.info?.[campo] ?? 0) - (a.info?.[campo] ?? 0) || a.name.localeCompare(b.name),
    );
  }

  protected onClasse(tag: string): void {
    this.classe.set(this.classe() === tag ? null : tag);
  }

  protected onOrdenacao(value: string): void {
    const valida = ['nome', 'nomeDesc', 'ataque', 'defesa', 'magia', 'dificuldade'] as const;
    if ((valida as readonly string[]).includes(value)) {
      this.ordenacao.set(value as (typeof valida)[number]);
    }
  }

  protected onSearch(value: string): void {
    this.query.set(value);
  }

  protected isFreeToPlay(c: DDragonChampion): boolean {
    return this.rotationIds().includes(Number(c.key));
  }

  /** Nível de maestria do invocador ativo para o campeão (null = sem badge). */
  protected maestriaDe(c: DDragonChampion): number | null {
    return this.summonerStore.levelFor(c.key);
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
