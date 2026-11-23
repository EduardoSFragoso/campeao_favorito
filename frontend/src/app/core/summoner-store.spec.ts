import { SummonerStore } from './summoner-store.service';
import { SummonerProfile } from './backend.service';

/** Store de maestria: alimenta os badges de nível do grid de campeões. */
describe('SummonerStore', () => {
  let store: SummonerStore;

  const perfil = {
    puuid: 'puuid-1',
    gameName: 'Hideonbush',
    tagLine: 'KR',
    name: 'Hide on bush',
    profileIconId: 10,
    summonerLevel: 300,
    ranked: [],
    topMasteries: [],
  } as unknown as SummonerProfile;

  beforeEach(() => {
    sessionStorage.clear();
    store = new SummonerStore();
  });

  it('começa vazio (nem memória nem badge)', () => {
    expect(store.current()).toBeNull();
    expect(store.levelFor('266')).toBeNull();
  });

  it('set mapeia maestria por key numérico e registra invocador/servidor', () => {
    store.set(
      perfil,
      [
        { championId: 266, championLevel: 7, championPoints: 123456, chestGranted: false },
        { championId: 412, championLevel: 3, championPoints: 999, chestGranted: true },
      ],
      'br1',
    );
    expect(store.levelFor('266')).toBe(7);
    expect(store.levelFor('412')).toBe(3);
    expect(store.levelFor('7')).toBeNull();
    const s = store.current()!;
    expect(s.gameName).toBe('Hideonbush');
    expect(s.platform).toBe('br1');
  });

  it('persiste em sessionStorage e relê numa nova instância (outra página da sessão)', () => {
    store.set(perfil, [{ championId: 266, championLevel: 7, championPoints: 1, chestGranted: false }], 'kr');
    const reaberto = new SummonerStore();
    expect(reaberto.current()!.gameName).toBe('Hideonbush');
    expect(reaberto.levelFor('266')).toBe(7);
  });

  it('clear apaga memória e sessão', () => {
    store.set(perfil, [], 'br1');
    store.clear();
    expect(store.current()).toBeNull();
    expect(new SummonerStore().current()).toBeNull();
  });
});
