import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { DataDragon } from './data-dragon.service';

/**
 * DataDragon.chromas() — fonte Community Dragon (JSON por campeão, sem chave).
 * Variante do payload capturada de Aatrox (266) em 23/11/2026:
 * todas as cromas de uma skin compartilham a mesma imagem (ChromaPreview),
 * o que diferencia cada croma é o par de hex em `colors`.
 */
describe('DataDragon — chromas()', () => {
  let service: DataDragon;
  let http: HttpTestingController;

  /** Response mínimo do CDragon: 2 skins com cromas + 1 sem (deve ser ignorada). */
  const resposta: object = {
    skins: [
      {
        num: 4,
        name: 'Mecha Aatrox',
        chromas: [
          { id: 266004, name: 'Mecha Aatrox (Obsidian)', colors: ['#27211C', '#27211C'] },
          { id: 266005, name: 'Mecha Aatrox (Pearl)', colors: ['#D6DBC9', '#C0939A'] },
        ],
      },
      {
        // skin sem nenhuma croma não pode virar entrada
        num: 5,
        name: 'Justicar Aatrox',
        chromas: [],
      },
      {
        // croma sem colors → amostra ganha fallback cinza neutro
        num: 12,
        name: 'Odyssey Aatrox',
        chromas: [{ id: 266012, name: 'Odyssey Aatrox (Ruby)', colors: [] }],
      },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(DataDragon);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('mapeia cromas por skin a partir do JSON do CDragon', async () => {
    const p = service.chromas('266');

    http
      .expectOne(
        'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champions/266.json',
      )
      .flush(resposta);

    const cromas = await p;
    expect(cromas).toEqual([
      { id: 266004, name: 'Mecha Aatrox (Obsidian)', colors: ['#27211C', '#27211C'], skinNum: 4 },
      { id: 266005, name: 'Mecha Aatrox (Pearl)', colors: ['#D6DBC9', '#C0939A'], skinNum: 4 },
      { id: 266012, name: 'Odyssey Aatrox (Ruby)', colors: ['#66666A'], skinNum: 12 },
    ]);
  });

  it('rejeita sem derrubar a página (CDN fora → UI só fica sem amostras)', async () => {
    const p = service.chromas('266').catch(() => []);
    http
      .expectOne(
        'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champions/266.json',
      )
      .flush('cdn fora', { status: 503, statusText: 'Service Unavailable' });

    await expect(p).resolves.toEqual([]);
  });
});
