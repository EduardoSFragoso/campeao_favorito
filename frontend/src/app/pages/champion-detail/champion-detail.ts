import { Component, computed, effect, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { DataDragon, ChampDetail, isRealSkin, ChromaRef } from '../../core/data-dragon.service';
import { Backend } from '../../core/backend.service';
import { Auth } from '../../core/auth.service';
import { messageOf } from '../../core/api-error';

@Component({
  selector: 'app-champion-detail',
  imports: [RouterLink],
  templateUrl: './champion-detail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './champion-detail.scss',
  host: {
    '(document:keydown.escape)': 'closeSkin()',
    // clique fora do card fecha (clicar dentro foi para o .card)
    '(document:click)': 'closeSkinOnOutsideClick($event)',
  },
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

  // ---------- card da skin (modal) e cromas ----------

  /** num da skin aberta no card (null = card fechado). */
  private readonly openSkinNum = signal<number | null>(null);
  /** id da croma selecionada dentro do card aberto. */
  private readonly selectedChromaId = signal<number | null>(null);

  /** cromas vindas do CDragon, agrupadas por num da skin. */
  private readonly chromasData = signal<Map<number, ChromaRef[]>>(new Map());
  private chromasKey: string | null = null;

  constructor() {
    // carrega as cromas quando o detalhe chega; CDN fora degrada sem amostras
    effect(() => {
      const c = this.data();
      if (!c) return;
      this.chromasKey = c.key;
      this.dd
        .chromas(c.key)
        .then((list) => {
          if (this.chromasKey !== c.key) return;
          const porSkin = new Map<number, ChromaRef[]>();
          for (const ch of list) {
            const grupo = porSkin.get(ch.skinNum);
            if (grupo) {
              grupo.push(ch);
            } else {
              porSkin.set(ch.skinNum, [ch]);
            }
          }
          this.chromasData.set(porSkin);
        })
        .catch(() => {
          if (this.chromasKey === c.key) this.chromasData.set(new Map());
        });
    });
  }

  /** Cromas de uma skin (para as amostras no grid e no card). */
  protected chromasOf(skinNum: number): ChromaRef[] {
    return this.chromasData().get(skinNum) ?? [];
  }

  /** Estado completo do card aberto (null quando fechado). */
  protected readonly modalSkin = computed(() => {
    const num = this.openSkinNum();
    if (num === null) return null;
    const skin = this.realSkins().find((s) => s.num === num);
    return skin ? { skin, chromas: this.chromasOf(skin.num) } : null;
  });

  /** Nome curto da croma selecionada, ex. "Mecha Aatrox (Obsidian)" → "Obsidian". */
  protected readonly selectedChromaLabel = computed(() => {
    const id = this.selectedChromaId();
    const m = this.modalSkin();
    if (id === null || !m) return null;
    const ch = m.chromas.find((x) => x.id === id);
    if (!ch) return null;
    const entreParenteses = /\(([^)]+)\)\s*$/.exec(ch.name);
    return entreParenteses ? entreParenteses[1] : ch.name;
  });

  protected openFromCard(skin: ChampDetail['skins'][number]): void {
    this.activeSkin.set(skin.num);
    this.openSkinNum.set(skin.num);
    this.selectedChromaId.set(null);
  }

  protected openFromChroma(ch: ChromaRef): void {
    this.activeSkin.set(ch.skinNum);
    this.openSkinNum.set(ch.skinNum);
    this.selectedChromaId.set(ch.id);
  }

  protected selectChroma(ch: ChromaRef): void {
    this.selectedChromaId.set(ch.id);
  }

  protected closeSkin(): void {
    this.openSkinNum.set(null);
    this.selectedChromaId.set(null);
  }

  /** Fecha o card quando o clique acontece fora dele. */
  protected closeSkinOnOutsideClick(ev: Event): void {
    if (this.openSkinNum() === null) return;
    const target = ev.target as HTMLElement | null;
    if (target?.closest?.('.skin-modal') && !target.closest('.skin-modal .card')) this.closeSkin();
  }

  /** Navega entre as skins reais com wrap-around. */
  protected stepSkin(dir: 1 | -1): void {
    const skins = this.realSkins();
    if (skins.length === 0) return;
    const idx = skins.findIndex((s) => s.num === this.openSkinNum());
    const novo = (idx + dir + skins.length) % skins.length;
    this.openSkinNum.set(skins[novo].num);
    this.selectedChromaId.set(null);
  }

  /** Gradiente CSS da amostra de croma (par de hex do CDragon). */
  protected chromaGradient(colors: string[]): string {
    const um = colors[0] ?? '#66666A';
    const dois = colors[1] ?? um;
    return `linear-gradient(135deg, ${um}, ${dois})`;
  }

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
