import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../core/services/api.service';
import { FormatService } from '../../core/services/format.service';
import { SessionResponse, StreakResponse } from '../../core/models';
import { FormatPointsPipe } from '../../shared/pipes/format-points.pipe';
import { FormatDurationPipe } from '../../shared/pipes/format-duration.pipe';
import { getStreakBonusPercentage } from '../../core/constants/points.constants';
import { startOfDay, endOfDay, startOfWeek, endOfWeek, startOfMonth, endOfMonth, startOfYear, endOfYear, format } from 'date-fns';

type Period = 'day' | 'week' | 'month' | 'year';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatProgressBarModule, FormatPointsPipe, FormatDurationPipe],
  templateUrl: './history.component.html',
  styleUrl: './history.component.scss',
})
export class HistoryComponent implements OnInit {
  private api = inject(ApiService);
  fmt = inject(FormatService);

  selectedPeriod = signal<Period>('day');
  sessions = signal<SessionResponse[]>([]);
  streak = signal<StreakResponse | null>(null);
  totalPoints = signal(0);
  goals = signal({ dayJobHours: 8, sideWorkHours: 2 });
  loading = signal(true);

  periods: Period[] = ['day', 'week', 'month', 'year'];

  dayJobMinutes = computed(() => this.sessions().filter(s => s.type === 'DAY_JOB').reduce((sum, s) => sum + s.durationMinutes, 0));
  sideWorkMinutes = computed(() => this.sessions().filter(s => s.type === 'SIDE_WORK' || s.type === 'EARLY_MORNING').reduce((sum, s) => sum + s.durationMinutes, 0));
  periodPoints = computed(() => this.sessions().reduce((sum, s) => sum + s.pointsEarned, 0));

  dayJobProgress = computed(() => {
    const goal = this.goals().dayJobHours * 60;
    return goal > 0 ? Math.min(this.dayJobMinutes() / goal, 1) : 0;
  });

  sideWorkProgress = computed(() => {
    const goal = this.goals().sideWorkHours * 60;
    return goal > 0 ? Math.min(this.sideWorkMinutes() / goal, 1) : 0;
  });

  streakBonus = computed(() => getStreakBonusPercentage(this.streak()?.currentStreak || 0));

  ngOnInit(): void {
    this.loadData();
  }

  selectPeriod(period: Period): void {
    this.selectedPeriod.set(period);
    this.loadSessions();
  }

  private loadData(): void {
    this.api.getTotalPoints().subscribe({ next: (r) => this.totalPoints.set(r.totalPoints) });
    this.api.getStreakInfo().subscribe({ next: (r) => this.streak.set(r) });
    this.api.getGoals().subscribe({ next: (r) => this.goals.set(r) });
    this.loadSessions();
  }

  private loadSessions(): void {
    this.loading.set(true);
    const now = new Date();
    let start: Date, end: Date;

    switch (this.selectedPeriod()) {
      case 'day': start = startOfDay(now); end = endOfDay(now); break;
      case 'week': start = startOfWeek(now, { weekStartsOn: 1 }); end = endOfWeek(now, { weekStartsOn: 1 }); break;
      case 'month': start = startOfMonth(now); end = endOfMonth(now); break;
      case 'year': start = startOfYear(now); end = endOfYear(now); break;
    }

    const startStr = format(start, "yyyy-MM-dd'T'HH:mm:ss");
    const endStr = format(end, "yyyy-MM-dd'T'HH:mm:ss");

    this.api.getSessions(startStr, endStr).subscribe({
      next: (sessions) => {
        this.sessions.set(sessions);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
