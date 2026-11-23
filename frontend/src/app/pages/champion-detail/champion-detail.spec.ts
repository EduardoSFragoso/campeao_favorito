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
      // se sobrou <a>, o href não pode disparar código
      const href = a.getAttribute('href')?.toLowerCase() ?? '';
      expect(href.startsWith('javascript:')).toBe(false);
    }
  });
});
