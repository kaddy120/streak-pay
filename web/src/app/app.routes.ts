import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
  },
  {
    path: 'wishlist',
    loadComponent: () => import('./features/wishlist/wishlist.component').then(m => m.WishlistComponent),
  },
  {
    path: 'wishlist/:id',
    loadComponent: () => import('./features/wish-detail/wish-detail.component').then(m => m.WishDetailComponent),
  },
  {
    path: 'sessions/:id',
    loadComponent: () => import('./features/session-detail/session-detail.component').then(m => m.SessionDetailComponent),
  },
  {
    path: 'history',
    loadComponent: () => import('./features/history/history.component').then(m => m.HistoryComponent),
  },
  {
    path: 'badges',
    loadComponent: () => import('./features/badges/badges.component').then(m => m.BadgesComponent),
  },
  {
    path: 'settings',
    loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent),
  },
  { path: '**', redirectTo: 'dashboard' },
];
