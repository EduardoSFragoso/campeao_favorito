import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Auth } from '../../core/auth.service';
import { Backend } from '../../core/backend.service';
import { DataDragon, DDragonChampion } from '../../core/data-dragon.service';
import { SummonerStore } from '../../core/summoner-store.service';
import { ChampionsPage } from './champions';

/**
 * Filtro por classe e ordenação do grid de campeões.
 * Dados fictícios com resultados conhecidos: 4 campeões de classes e infos distintas.
 */
describe('ChampionsPage — filtro por classe e ordenação', () => {
  let fixture: ComponentFixture<ChampionsPage>;
  let el: HTMLElement;

  function camp(
    id: string,
    key: string,
    name: string,
    tags: string[],
    info: { attack: number; defense: number; magic: number; difficulty: number },
  ): DDragonChampion {
    return { id, key, name, title: `titulo de ${name}`, blurb: '', tags, info };
  }

  /** Ordem alfabética já garantida pelo serviço no app real (stub replica). */
  const lista: DDragonChampion[] = [
    camp('Aatrox', '266', 'Aatrox', ['Fighter'], { attack: 8, defense: 4, magic: 3, difficulty: 4 }),
    camp('Ashen', '22', 'Ashen', ['Marksman', 'Support'], {
      attack: 7,
      defense: 3,
      magic: 2,
      difficulty: 6,
    }),
    camp('Lux', '99', 'Lux', ['Mage', 'Support'], { attack: 2, defense: 4, magic: 9, difficulty: 5 }),
    camp('Zed', '238', 'Zed', ['Assassin'], { attack: 9, defense: 2, magic: 0, difficulty: 7 }),
  ];

  /** maestria "ativa" do último invocador buscado (mutável dentro dos testes) */
  const niveis: Record<string, number> = { '266': 7 };
  const storeStub = {
    current: () =>
      Object.keys(niveis).length
        ? {
            puuid: 'p1',
            gameName: 'Hideonbush',
            tagLine: 'KR',
            platform: 'br1',
            levels: { ...niveis },
          }
        : null,
    levelFor: (k: string) => niveis[k] ?? null,
    clear: () => {
      for (const k of Object.keys(niveis)) delete niveis[k];
    },
  };

  const ddStub = {
    listChampions: () => of(lista),
    loading: () => '',
  } as unknown as DataDragon;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChampionsPage],
      providers: [
        provideRouter([]),
        { provide: DataDragon, useValue: ddStub },
        {
          provide: Backend,
          useValue: { rotation: () => of({ freeChampionIds: [] }), isFavorite: () => false },
        },
        { provide: Auth, useValue: { isLoggedIn: () => false } },
        { provide: SummonerStore, useValue: storeStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChampionsPage);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    for (let i = 0; i < 3 && el.querySelector('.card') === null; i++) {
      await fixture.whenStable();
      await new Promise<void>((r) => setTimeout(r, 0));
      fixture.detectChanges();
    }
  });

  function nomes(): string[] {
    return [...el.querySelectorAll('.card h3')].map((n) => n.textContent!.trim());
  }

  it('sem filtro, mostra quarto, na ordem alfabética padrão', () => {
    expect(nomes()).toEqual(['Aatrox', 'Ashen', 'Lux', 'Zed']);
  });

  it('filtra por classe: Mago deixa só Lux', () => {
    const mago = [...el.querySelectorAll('.chip')].find((c) =>
      c.textContent!.includes('Mago'),
    ) as HTMLElement;
    expect(mago, 'chip Mago deve existir').toBeTruthy();
    mago.click();
    fixture.detectChanges();
    expect(nomes()).toEqual(['Lux']);
  });

  it('chip "Todas" volta ao estado sem filtro', () => {
    const mago = [...el.querySelectorAll('.chip')].find((c) =>
      c.textContent!.includes('Mago'),
    ) as HTMLElement;
    mago.click();
    fixture.detectChanges();
    const todas = [...el.querySelectorAll('.chip')].find((c) =>
      c.textContent!.includes('Todas'),
    ) as HTMLElement;
    todas.click();
    fixture.detectChanges();
    expect(nomes().length).toBe(4);
  });

  it('busca e filtro combinam: Mago + nada que combine → vazio', () => {
    const mago = [...el.querySelectorAll('.chip')].find((c) =>
      c.textContent!.includes('Mago'),
    ) as HTMLElement;
    mago.click();
    fixture.detectChanges();
    const input = el.querySelector<HTMLInputElement>('.toolbar input')!;
    input.value = 'Aatrox';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
    // Aatrox não é Mago: o filtro de classe vence a busca
    expect(nomes()).toEqual([]);
    expect(el.textContent).toContain('Nenhum campeão');
  });

  it('ordena por ataque (desc) com nome como desempate', () => {
    const select = el.querySelector<HTMLSelectElement>('.toolbar select')!;
    select.value = 'ataque';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    expect(nomes()).toEqual(['Zed', 'Aatrox', 'Ashen', 'Lux']);
  });

  it('ordena por magia (desc)', () => {
    const select = el.querySelector<HTMLSelectElement>('.toolbar select')!;
    select.value = 'magia';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    expect(nomes()).toEqual(['Lux', 'Aatrox', 'Ashen', 'Zed']);
  });

  it('ordena por nome desc', () => {
    const select = el.querySelector<HTMLSelectElement>('.toolbar select')!;
    select.value = 'nomeDesc';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    expect(nomes()).toEqual(['Zed', 'Lux', 'Ashen', 'Aatrox']);
  });
});

describe('ChampionsPage — badge de maestria no grid', () => {
  let fixture: ComponentFixture<ChampionsPage>;
  let el: HTMLElement;

  const niveis: Record<string, number> = { '266': 7 };
  const storeStub = {
    current: () =>
      Object.keys(niveis).length
        ? { puuid: 'p1', gameName: 'Hideonbush', tagLine: 'KR', platform: 'br1', levels: { ...niveis } }
        : null,
    levelFor: (k: string) => niveis[k] ?? null,
    clear: () => {
      for (const k of Object.keys(niveis)) delete niveis[k];
    },
  };

  const lista: DDragonChampion[] = [
    {
      id: 'Aatrox',
      key: '266',
      name: 'Aatrox',
      title: 't',
      blurb: '',
      tags: ['Fighter'],
      info: { attack: 8, defense: 4, magic: 3, difficulty: 4 },
    },
    {
      id: 'Lux',
      key: '99',
      name: 'Lux',
      title: 't',
      blurb: '',
      tags: ['Mage'],
      info: { attack: 2, defense: 4, magic: 9, difficulty: 5 },
    },
  ];

  const ddStub = {
    listChampions: () => of(lista),
    loading: () => '',
  } as unknown as DataDragon;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChampionsPage],
      providers: [
        provideRouter([]),
        { provide: DataDragon, useValue: ddStub },
        {
          provide: Backend,
          useValue: { rotation: () => of({ freeChampionIds: [] }), isFavorite: () => false },
        },
        { provide: Auth, useValue: { isLoggedIn: () => false } },
        { provide: SummonerStore, useValue: storeStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChampionsPage);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    for (let i = 0; i < 3 && el.querySelector('.card') === null; i++) {
      await fixture.whenStable();
      await new Promise<void>((r) => setTimeout(r, 0));
      fixture.detectChanges();
    }
  });

  it('badge de maestria aparece só no campeão com nível', () => {
    const badges = [...el.querySelectorAll('.badge.mastery')];
    expect(badges.length).toBe(1);
    expect(badges[0].textContent).toContain('nível 7');
    // e o rótulo de origem aparece no topo do grid
    expect(el.querySelector('.mastery-source')!.textContent).toContain('Hideonbush#KR');
  });

  it('limpar remove a linha de origem e os badges', () => {
    (el.querySelector('.mastery-clear') as HTMLElement).click();
    fixture.detectChanges();
    expect(el.querySelector('.mastery-source')).toBeNull();
    expect(el.querySelectorAll('.badge.mastery').length).toBe(0);
  });
});
