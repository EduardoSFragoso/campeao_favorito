import { resolveSummonerQuery } from './tag-resolver';

describe('resolveSummonerQuery', () => {
  it('só nome: usa a tag padrão da plataforma do dropdown, sem derivar plataforma', () => {
    const q = resolveSummonerQuery('galuverior', 'br1');
    expect(q).toEqual({ gameName: 'galuverior', tagLine: 'BR1', platform: null });
  });

  it('GameName#TAG canônico: derivar plataforma e manter a tag', () => {
    const q = resolveSummonerQuery('hideonbush#KR', 'br1');
    expect(q).toEqual({ gameName: 'hideonbush', tagLine: 'KR', platform: 'kr' });
  });

  it('alias BR (minúsculo, com espaços): deriva br1 e canonicaliza tag para BR1', () => {
    const q = resolveSummonerQuery('  galuverior # br ', 'kr');
    expect(q).toEqual({ gameName: 'galuverior', tagLine: 'BR1', platform: 'br1' });
  });

  it('aliases por região: NA→na1, EUW/EUW1→euw1, EUN→eun1, LAS→la2, OCE→oc1, TR→tr1', () => {
    const cases: [string, string, string][] = [
      ['a#NA', 'na1', 'NA1'],
      ['a#EUW', 'euw1', 'EUW'],
      ['a#EUW1', 'euw1', 'EUW'],
      ['a#EUN', 'eun1', 'EUNE'],
      ['a#LAS', 'la2', 'LAS'],
      ['a#OCE', 'oc1', 'OCE'],
      ['a#TR', 'tr1', 'TR1'],
    ];
    for (const [raw, platform, tagLine] of cases) {
      expect(resolveSummonerQuery(raw, 'br1')).toEqual({
        gameName: 'a',
        tagLine,
        platform,
      });
    }
  });

  it('tag desconhecida: mantém a plataforma do dropdown e usa a tag digitada', () => {
    const q = resolveSummonerQuery('Faker# XYZ', 'kr');
    expect(q).toEqual({ gameName: 'Faker', tagLine: 'XYZ', platform: null });
  });

  it('nome com # no fim ("Name#"): vira só nome com tag padrão', () => {
    expect(resolveSummonerQuery('Name#', 'br1')).toEqual({
      gameName: 'Name#',
      tagLine: 'BR1',
      platform: null,
    });
  });

  it('nome vazio: null (não chama API)', () => {
    expect(resolveSummonerQuery('', 'br1')).toBeNull();
    expect(resolveSummonerQuery('   ', 'br1')).toBeNull();
  });

  it('# no começo ("#KR") é nome literal, não tag — comportamento preservado', () => {
    expect(resolveSummonerQuery('#KR', 'br1')).toEqual({
      gameName: '#KR',
      tagLine: 'BR1',
      platform: null,
    });
  });

  it('plataforma sem tag padrão: cai em BR1', () => {
    expect(resolveSummonerQuery('a', 'plataforma-inexistente')).toEqual({
      gameName: 'a',
      tagLine: 'BR1',
      platform: null,
    });
  });

  it("nome com apóstrofo/ponto sobrevive ao parsing (ex.: O'Brien.X)", () => {
    expect(resolveSummonerQuery("O'Brien.X#kr", 'br1')?.gameName).toBe("O'Brien.X");
  });
});
