/**
 * Resolução da busca de invocador: "GameName#TAG" (ou só nome + plataforma corrente do dropdown)
 * → gameName + tagLine canônica + plataforma derivada. Puro e sem Angular — por isso testável
 * isolado (tag-resolver.spec.ts).
 */

/** Tag de Riot ID padrão de cada plataforma (KR = "KR", EUW = "EUW" etc.). */
export const DEFAULT_TAG: Record<string, string> = {
  br1: 'BR1',
  na1: 'NA1',
  euw1: 'EUW',
  eun1: 'EUNE',
  kr: 'KR',
  jp1: 'JP1',
  la1: 'LAN',
  la2: 'LAS',
  oc1: 'OCE',
  tr1: 'TR1',
};

/** Tag digitada → plataforma que serve Summoner/League/Mastery (com aliases comuns). */
const TAG_TO_PLATFORM: Record<string, string> = {
  BR: 'br1',
  BR1: 'br1',
  NA: 'na1',
  NA1: 'na1',
  EUW: 'euw1',
  EUW1: 'euw1',
  EUN: 'eun1',
  EUNE: 'eun1',
  EUN1: 'eun1',
  KR: 'kr',
  KR1: 'kr',
  JP: 'jp1',
  JP1: 'jp1',
  LAN: 'la1',
  LA1: 'la1',
  LAS: 'la2',
  LA2: 'la2',
  OCE: 'oc1',
  OC1: 'oc1',
  TR: 'tr1',
  TR1: 'tr1',
};

export interface SummonerQuery {
  gameName: string;
  tagLine: string;
  /**
   * Plataforma a usar para a busca. `null` = não derivada da tag — mantém a plataforma
   * corrente do dropdown (só-nome, ou tag desconhecida).
   */
  platform: string | null;
}

/**
 * Resolve o campo único de busca.
 * @param raw texto digitado ("galuverior", "hideonbush#kr", "Name # LA2"…)
 * @param currentPlatform plataforma atual do dropdown (usada cuando não há tag útil)
 * @returns null quando não dá para montar busca (sem nome)
 */
export function resolveSummonerQuery(raw: string, currentPlatform: string): SummonerQuery | null {
  const text = raw.trim();
  if (!text) return null;

  // "GameName#TAG" → tag explícita (e plataforma derivada); só nome → tag padrão do dropdown
  const hash = text.lastIndexOf('#');
  let gameName = text;
  let tagLine = DEFAULT_TAG[currentPlatform] ?? 'BR1';
  let platform: string | null = null;
  if (hash > 0 && hash < text.length - 1) {
    gameName = text.slice(0, hash).trim();
    const typed = text
      .slice(hash + 1)
      .trim()
      .toUpperCase();
    const plat = TAG_TO_PLATFORM[typed];
    if (plat) {
      // tag conhecida: deriva a plataforma e canonicaliza (BR→BR1, KR→KR…)
      platform = plat;
      tagLine = DEFAULT_TAG[plat] ?? typed;
    } else {
      tagLine = typed;
    }
  }
  if (!gameName || !tagLine) return null;

  return { gameName, tagLine, platform };
}
