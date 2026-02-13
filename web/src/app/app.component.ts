import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { ThemeService } from './core/services/theme.service';
import { SseService } from './core/services/sse.service';
import { ApiService } from './core/services/api.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatIconModule, MatButtonModule,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent implements OnInit {
  private themeService = inject(ThemeService);
  private sseService = inject(SseService);
  private apiService = inject(ApiService);

  darkMode = this.themeService.darkMode;
  connectionState = this.sseService.connectionState;
  totalPoints = signal(0);
  mobileMenuOpen = signal(false);

  navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
    { label: 'Wish List', icon: 'redeem', route: '/wishlist' },
    { label: 'History', icon: 'bar_chart', route: '/history' },
    { label: 'Badges', icon: 'military_tech', route: '/badges' },
  ];

  bottomNavItems: NavItem[] = [
    { label: 'Settings', icon: 'settings', route: '/settings' },
  ];

  ngOnInit(): void {
    this.sseService.connect();
    this.loadPoints();

    this.sseService.statsUpdated$.subscribe((stats) => {
      this.totalPoints.set(stats.totalPoints);
    });
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen.update(v => !v);
  }

  private loadPoints(): void {
    this.apiService.getTotalPoints().subscribe({
      next: (res) => this.totalPoints.set(res.totalPoints),
    });
  }
}
