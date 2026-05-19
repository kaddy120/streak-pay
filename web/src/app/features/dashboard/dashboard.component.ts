import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { SseService } from '../../core/services/sse.service';
import { TimerService } from '../../core/services/timer.service';
import { FormatService } from '../../core/services/format.service';
import {
  DashboardResponse, SessionResponse, SessionType,
  DeviceStatusResponse, BadgeDto, WishItemResponse, StreakResponse,
} from '../../core/models';
import { SESSION_COLORS, SESSION_LABELS, SESSION_ICONS } from '../../core/constants/session-colors';
import { getBadgeGradient } from '../../core/constants/badge-gradients';
import { CURRENCY_TO_POINTS_RATE } from '../../core/constants/points.constants';
import { FormatPointsPipe } from '../../shared/pipes/format-points.pipe';
import { FormatDurationPipe } from '../../shared/pipes/format-duration.pipe';
import { SessionTypeLabelPipe } from '../../shared/pipes/session-type-label.pipe';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatButtonModule,
    MatProgressBarModule, MatTooltipModule,
    FormatPointsPipe, FormatDurationPipe, SessionTypeLabelPipe,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private sse = inject(SseService);
  private router = inject(Router);
  fmt = inject(FormatService);
  timer = inject(TimerService);

  private subs: Subscription[] = [];

  // State
  userName = signal('');
  totalPoints = signal(0);
  streak = signal<StreakResponse | null>(null);
  motivationalMessage = signal('');
  badges = signal<BadgeDto[]>([]);
  highlightedBadges = signal<BadgeDto[]>([]);
  recentSessions = signal<SessionResponse[]>([]);
  activeSessions = signal<SessionResponse[]>([]);
  devices = signal<DeviceStatusResponse[]>([]);
  goals = signal({ dayJobHours: 8, sideWorkHours: 2 });
  nextWishItem = signal<WishItemResponse | null>(null);
  loading = signal(true);

  // Current local session
  currentSessionId = signal<number | null>(null);

  // Computed
  wishProgress = computed(() => {
    const wish = this.nextWishItem();
    if (!wish) return 0;
    const needed = wish.price / CURRENCY_TO_POINTS_RATE;
    return Math.min(this.totalPoints() / needed, 1);
  });

  wishPointsNeeded = computed(() => {
    const wish = this.nextWishItem();
    if (!wish) return 0;
    const needed = wish.price / CURRENCY_TO_POINTS_RATE;
    return Math.max(needed - this.totalPoints(), 0);
  });

  // Remote session (active on another device, not local)
  remoteSession = computed(() => {
    const active = this.activeSessions();
    if (active.length === 0) return null;
    // Show the first active session that's not our web session
    return active.find(s => s.deviceId !== 'web') || null;
  });

  readonly SESSION_COLORS = SESSION_COLORS;
  readonly SESSION_LABELS = SESSION_LABELS;
  readonly SESSION_ICONS = SESSION_ICONS;
  readonly getBadgeGradient = getBadgeGradient;

  ngOnInit(): void {
    this.loadDashboard();
    this.loadWishItems();
    this.setupSseListeners();
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }

  private loadDashboard(): void {
    this.api.getDashboard().subscribe({
      next: (data: DashboardResponse) => {
        this.userName.set(data.userName);
        this.totalPoints.set(data.totalPoints);
        this.streak.set(data.streak);
        this.motivationalMessage.set(data.badges.motivationalMessage);
        this.badges.set(data.badges.badges);
        this.highlightedBadges.set(data.badges.highlightedBadges);
        this.recentSessions.set(data.recentSessions);
        this.activeSessions.set(data.activeSessions);
        this.devices.set(data.deviceStatuses);
        this.goals.set(data.goals);
        this.loading.set(false);

        // Sync timer with active web session if any
        const webSession = data.activeSessions.find(s => s.deviceId === 'web');
        if (webSession) {
          this.currentSessionId.set(webSession.id);
          if (webSession.isPaused) {
            this.timer.start(webSession.activeElapsedSeconds);
            this.timer.pause();
          } else {
            this.timer.start(webSession.activeElapsedSeconds);
          }
        }
      },
      error: () => this.loading.set(false),
    });
  }

  private loadWishItems(): void {
    this.api.getWishItems('available').subscribe({
      next: (items) => {
        if (items.length > 0) {
          // Find cheapest available item
          const sorted = [...items].sort((a, b) => a.price - b.price);
          this.nextWishItem.set(sorted[0]);
        }
      },
    });
  }

  private setupSseListeners(): void {
    this.subs.push(
      this.sse.sessionCreated$.subscribe(session => {
        this.recentSessions.update(list => [session, ...list].slice(0, 10));
        if (!session.endTime) {
          this.activeSessions.update(list => [...list, session]);
        }
      }),
      this.sse.sessionUpdated$.subscribe(session => {
        this.recentSessions.update(list =>
          list.map(s => s.id === session.id ? session : s)
        );
        if (session.endTime) {
          this.activeSessions.update(list => list.filter(s => s.id !== session.id));
          if (this.currentSessionId() === session.id) {
            this.timer.stop();
            this.currentSessionId.set(null);
          }
        } else {
          this.activeSessions.update(list => {
            const idx = list.findIndex(s => s.id === session.id);
            return idx >= 0 ? list.map(s => s.id === session.id ? session : s) : [...list, session];
          });
          // Sync local timer when this web session is updated remotely
          if (this.currentSessionId() === session.id) {
            this.timer.syncElapsed(session.activeElapsedSeconds);
            if (session.isPaused && !this.timer.paused()) {
              this.timer.pause();
            } else if (!session.isPaused && this.timer.paused()) {
              this.timer.resume();
            }
          }
        }
      }),
      this.sse.sessionDeleted$.subscribe(id => {
        this.recentSessions.update(list => list.filter(s => s.id !== id));
        this.activeSessions.update(list => list.filter(s => s.id !== id));
      }),
      this.sse.statsUpdated$.subscribe(stats => {
        this.totalPoints.set(stats.totalPoints);
        this.streak.set(stats.streak);
      }),
    );
  }

  // Timer actions
  startSession(): void {
    const now = this.fmt.toLocalISO();
    this.api.createSession({
      deviceId: 'web',
      startTime: now,
      type: SessionType.SIDE_WORK, // Default, server will determine based on time
    }).subscribe({
      next: (session) => {
        this.currentSessionId.set(session.id);
        this.timer.start(0);
      },
    });
  }

  pauseSession(): void {
    const id = this.currentSessionId();
    if (!id) return;
    this.api.updateSession(id, { isPaused: true, pausedAt: this.fmt.toLocalISO() }).subscribe({
      next: () => this.timer.pause(),
    });
  }

  resumeSession(): void {
    const id = this.currentSessionId();
    if (!id) return;
    this.api.updateSession(id, { isPaused: false }).subscribe({
      next: () => this.timer.resume(),
    });
  }

  stopSession(): void {
    const id = this.currentSessionId();
    if (!id) return;
    const elapsed = this.timer.elapsed();
    this.timer.stop();
    this.currentSessionId.set(null);
    if (elapsed < 900) {
      // Under 15 min — discard: delete from API and remove from local lists
      this.api.deleteSession(id).subscribe({
        next: () => {
          this.activeSessions.update(list => list.filter(s => s.id !== id));
          this.recentSessions.update(list => list.filter(s => s.id !== id));
        },
      });
    } else {
      this.api.updateSession(id, { endTime: this.fmt.toLocalISO() }).subscribe({
        next: () => this.loadDashboard(),
      });
    }
  }

  // Remote session control
  pauseRemoteSession(): void {
    const rs = this.remoteSession();
    if (!rs) return;
    this.api.sendCommand({ deviceId: rs.deviceId, action: 'pause' }).subscribe();
  }

  resumeRemoteSession(): void {
    const rs = this.remoteSession();
    if (!rs) return;
    this.api.sendCommand({ deviceId: rs.deviceId, action: 'resume' }).subscribe();
  }

  stopRemoteSession(): void {
    const rs = this.remoteSession();
    if (!rs) return;
    this.api.sendCommand({ deviceId: rs.deviceId, action: 'stop' }).subscribe();
  }

  // Navigation
  goToSession(id: number): void {
    this.router.navigate(['/sessions', id]);
  }

  getSessionColor(type: SessionType): string {
    return SESSION_COLORS[type];
  }

  getDeviceStateLabel(state: string): string {
    const map: Record<string, string> = {
      ACTIVE: 'Active',
      PAUSED: 'Paused',
      IDLE: 'Idle',
    };
    return map[state] || state;
  }

  isHighlighted(badge: BadgeDto): boolean {
    return this.highlightedBadges().some(b => b.name === badge.name);
  }

  getDeviceStateColor(state: string): string {
    const map: Record<string, string> = {
      ACTIVE: '#4CAF50',
      PAUSED: '#FF9800',
      IDLE: '#9E9E9E',
    };
    return map[state] || '#9E9E9E';
  }
}
