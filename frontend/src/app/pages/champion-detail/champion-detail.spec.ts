import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Auth } from '../../core/auth.service';
import { Backend } from '../../core/backend.service';
import { DataDragon, ChampDetail } from '../../core/data-dragon.service';
import { ChampionDetailPage } from './champion-detail';

/**
 * Ponto de injeção de HTML do detalhe do campeão: a description das habilidades
 * (conteúdo do CDN Data Dragon) é renderizada com [innerHTML]. O Angular sanitiza
 * conteúdo HTML automaticamente — este teste prova que o pipeline real do componente
 * remove conteúdo ativo (script/iframe/onerror/javascript:) do render.
 */
describe('ChampionDetailPage — [innerHTML] sanitizado', () => {
  let fixture: ComponentFixture<ChampionDetailPage>;
  let el: HTMLElement;

  const MALICIOUS =
    '<p>ok</p><script>alert(1)</script>' +
    '<img src="x" onerror="alert(1)">' +
    '<iframe src="https://evil.example"></iframe>' +
    '<a href="javascript:alert(1)">clique</a>';

  function champMaligno(): ChampDetail {
    return {
      id: 'Lux',
      name: 'Lux',
      title: 'A Dama Luminosa',
      tags: ['Mage'],
      lore: 'lore limpa',
      info: { attack: 2, defense: 4, magic: 9, difficulty: 5 },
      spells: [
        {
          id: 'LuxLightBinding',
          name: 'Prisão de Luz',
          description: MALICIOUS,
          image: { full: 'LuxQ.png' },
        },
      ],
      skins: [{ id: 1000, num: 0, name: 'default', chromas: false }],
    } as unknown as ChampDetail;
  }

  const ddStub = {
    championDetail: () => of(champMaligno()),
    chromas: () => Promise.resolve([]),
    splash: () => '',
    spellIcon: (full: string) => full,
    // no-through: a página nunca referencia version/ensureChampionMap no caminho testado
    loadVersion: () => Promise.resolve(),
    ensureChampionMap: () => Promise.resolve(),
  } as unknown as DataDragon;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChampionDetailPage],
      providers: [
        provideRouter([]),
        // DEPOIS do provideRouter: sobrepõe a rota raiz (sem id) pela do campeão
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ id: 'Lux' })),
          } as unknown as ActivatedRoute,
        },
        { provide: DataDragon, useValue: ddStub },
        { provide: Backend, useValue: { isFavorite: () => false } },
        { provide: Auth, useValue: { isLoggedIn: () => false } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChampionDetailPage);
    el = fixture.nativeElement as HTMLElement;
    // toSignal + zoneless/OnPush: espera o parâmetro da rota e a emissão do stub de Data Dragon
    fixture.detectChanges();
    for (let i = 0; i < 3 && el.querySelector('.detail') === null; i++) {
      await fixture.whenStable();
      await new Promise<void>((r) => setTimeout(r, 0));
      fixture.detectChanges();
    }
    // sanidade: o campeão do stub precisa estar renderizado antes de qualquer asserção
    expect(
      el.querySelector('.detail'),
      'html=' + el.innerHTML.replace(/\s+/g, ' ').slice(0, 250),
    ).not.toBeNull();
  });

  it('renderiza o texto legível do conteúdo', () => {
    const comp = fixture.componentInstance as unknown as Record<string, () => ChampDetail | null>;
    const c = comp['data']();
    expect(c?.id, 'data=' + JSON.stringify(c ?? null)).toBe('Lux');
    const p = el.querySelector('.spells p');
    expect(p, 'html=' + el.innerHTML.replace(/\s+/g, ' ').slice(0, 300)).not.toBeNull();
    expect(p!.textContent).toContain('ok');
  });

  it('remove script', () => {
    expect(el.querySelector('.spells p script')).toBeNull();
  });

  it('remove iframe', () => {
    expect(el.querySelector('.spells p iframe')).toBeNull();
  });

  it('remove handler inline (onerror)', () => {
    expect(el.querySelector('.spells p')!.innerHTML).not.toContain('onerror');
  });

  it('não renderiza link javascript:', () => {
    const a = el.querySelector('.spells p a');
    if (a) {
      // se sobrou <a>, o href não pode disparar código (javascript:, data:, vbscript:)
      const href = a.getAttribute('href')?.toLowerCase() ?? '';
      expect(/^(javascript|data|vbscript):/.test(href)).toBe(false);
    }
  });
});

/**
 * Card da skin (modal em alta resolução) + cromas do Community Dragon.
 * Cenário: 3 skins, uma delas com cromas no payload do CDragon.
 */
describe('ChampionDetailPage — card da skin e cromas', () => {
  let fixture: ComponentFixture<ChampionDetailPage>;
  let el: HTMLElement;

  function champComSkins(): ChampDetail {
    return {
      id: 'Aatrox',
      key: '266',
      name: 'Aatrox',
      title: 'A Espada Darkin',
      tags: ['Fighter'],
      lore: 'lore',
      info: { attack: 8, defense: 4, magic: 3, difficulty: 4 },
      spells: [{ id: 'AatroxQ', name: 'Lâmina', description: '<p>q</p>', image: { full: 'AatroxQ.png' } }],
      skins: [
        { id: 266000, num: 0, name: 'default', chromas: false },
        { id: 266004, num: 4, name: 'Mecha Aatrox', chromas: true },
        { id: 266012, num: 12, name: 'Odyssey Aatrox', chromas: true },
      ],
    } as unknown as ChampDetail;
  }

  /** cromas que o stub do serviço "busca" no CDragon */
  const cromas = [
    { id: 266004, name: 'Mecha Aatrox (Obsidian)', colors: ['#27211C', '#27211C'], skinNum: 4 },
    { id: 266005, name: 'Mecha Aatrox (Pearl)', colors: ['#D6DBC9', '#C0939A'], skinNum: 4 },
  ];

  const ddStub = {
    championDetail: () => of(champComSkins()),
    chromas: () => Promise.resolve(cromas),
    splash: (_id: string, num: number) => `splash_${num}`,
    spellIcon: (full: string) => full,
    loadVersion: () => Promise.resolve(),
    ensureChampionMap: () => Promise.resolve(),
  } as unknown as DataDragon;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChampionDetailPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: 'Aatrox' })) } as unknown as ActivatedRoute,
        },
        { provide: DataDragon, useValue: ddStub },
        { provide: Backend, useValue: { isFavorite: () => false } },
        { provide: Auth, useValue: { isLoggedIn: () => false } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChampionDetailPage);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    for (let i = 0; i < 3 && el.querySelector('.skin-card') === null; i++) {
      await fixture.whenStable();
      await new Promise<void>((r) => setTimeout(r, 0));
      fixture.detectChanges();
    }
    // espera também as cromas (promise do CDragon resolve depois do render inicial)
    for (let i = 0; i < 3 && el.querySelector('.chroma') === null; i++) {
      await fixture.whenStable();
      await new Promise<void>((r) => setTimeout(r, 0));
      fixture.detectChanges();
    }
    // sanidade: skins renderizadas
    expect(el.querySelectorAll('.skin').length).toBe(3);
  });

  function clica(alm: Element | null): void {
    expect(alm, 'elemento deve existir no render').not.toBeNull();
    alm!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();
  }

  it('clicar na skin abre o card com o splash em alta resolução', () => {
    clica(el.querySelectorAll('.skin')[1]);
    const dialog = el.querySelector('.skin-modal .card');
    expect(dialog).not.toBeNull();
    expect(dialog!.getAttribute('aria-label')).toBe('Mecha Aatrox');
    const img = dialog!.querySelector('img');
    expect(img!.getAttribute('src')).toBe('splash_4');
  });

  it('fecha o card com o botão ✕', () => {
    clica(el.querySelectorAll('.skin')[1]);
    expect(el.querySelector('.skin-modal')).not.toBeNull();
    clica(el.querySelector('.skin-modal .close'));
    expect(el.querySelector('.skin-modal')).toBeNull();
  });

  it('fecha o card ao clicar no fundo, mas não no card', () => {
    clica(el.querySelectorAll('.skin')[1]);
    clica(el.querySelector('.skin-modal'));
    expect(el.querySelector('.skin-modal')).toBeNull();
    clica(el.querySelectorAll('.skin')[1]);
    clica(el.querySelector('.skin-modal .card'));
    expect(el.querySelector('.skin-modal')).not.toBeNull();
  });

  it('fecha o card com Esc', () => {
    clica(el.querySelectorAll('.skin')[1]);
    expect(el.querySelector('.skin-modal')).not.toBeNull();
    el.ownerDocument.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(el.querySelector('.skin-modal')).toBeNull();
  });

  it('navega ←/→ entre as skins dentro do card', () => {
    clica(el.querySelectorAll('.skin')[1]); // Mecha
    clica(el.querySelector('.skin-modal .nav.next'));
    expect(el.querySelector('.skin-modal .card img')!.getAttribute('src')).toBe('splash_12');
    clica(el.querySelector('.skin-modal .nav.next'));
    // dá a volta (wrap): última → primeira
    expect(el.querySelector('.skin-modal .card img')!.getAttribute('src')).toBe('splash_0');
    clica(el.querySelector('.skin-modal .nav.prev'));
    expect(el.querySelector('.skin-modal .card img')!.getAttribute('src')).toBe('splash_12');
  });

  it('mostra amostras das cromas da skin no grid', () => {
    const cartoes = el.querySelectorAll('.skin-card');
    expect(cartoes[0].querySelectorAll('.chroma').length).toBe(0);
    const amostras = cartoes[1].querySelectorAll('.chroma');
    expect(amostras.length).toBe(2);
    // gradiente construído a partir dos hex do CDragon (#27211C → rgb 39,33,28
    // no jsdom, que normaliza hex em style serializado)
    expect(amostras[0].getAttribute('style')).toContain('linear-gradient');
    expect(amostras[0].getAttribute('style')).toContain('rgb(39, 33, 28)');
  });

  it('clicar na croma abre o card da skin com a croma em destaque', () => {
    clica(el.querySelectorAll('.skin-card')[1].querySelectorAll('.chroma')[0]);
    const dialog = el.querySelector('.skin-modal .card');
    expect(dialog).not.toBeNull();
    expect(dialog!.querySelector('.chroma-name')!.textContent).toContain('Obsidian');
    // e a amostra correspondente fica marcada como selecionada dentro do card
    const selecionada = dialog!.querySelector('.chroma.selected');
    expect(selecionada).not.toBeNull();
  });
});
