import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/champions/champions').then((m) => m.ChampionsPage),
    title: 'Campeões — Campeões Favoritos LOL',
  },
  {
    path: 'champion/:id',
    loadComponent: () =>
      import('./pages/champion-detail/champion-detail').then((m) => m.ChampionDetailPage),
    title: 'Campeão — Campeões Favoritos LOL',
  },
  {
    path: 'favoritos',
    loadComponent: () => import('./pages/favorites/favorites').then((m) => m.FavoritesPage),
    title: 'Favoritos — Campeões Favoritos LOL',
  },
  {
    path: 'invocador',
    loadComponent: () => import('./pages/summoner/summoner').then((m) => m.SummonerPage),
    title: 'Invocador — Campeões Favoritos LOL',
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/auth/auth').then((m) => m.AuthPage),
    title: 'Entrar — Campeões Favoritos LOL',
  },
  { path: '**', redirectTo: '' },
];
