import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { ApiService } from '../../core/services/api.service';
import { BadgeDto } from '../../core/models';
import { getBadgeGradient } from '../../core/constants/badge-gradients';
import { POINT_RATES, STREAK_BONUSES } from '../../core/constants/points.constants';

interface BadgeInfo {
  name: string;
  displayName: string;
  description: string;
  icon: string;
  isPermanent: boolean;
}

@Component({
  selector: 'app-badges',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  templateUrl: './badges.component.html',
  styleUrl: './badges.component.scss',
})
export class BadgesComponent implements OnInit {
  private api = inject(ApiService);

  earnedBadges = signal<BadgeDto[]>([]);
  loading = signal(true);

  readonly getBadgeGradient = getBadgeGradient;

  readonly pointRates = [
    { type: 'Day Job', rate: POINT_RATES.DAY_JOB, color: '#FF9800', time: 'Weekdays 9:00-16:00', icon: 'work' },
    { type: 'Side Work', rate: POINT_RATES.SIDE_WORK, color: '#4CAF50', time: 'Outside work hours', icon: 'code' },
    { type: 'Early Morning', rate: POINT_RATES.EARLY_MORNING, color: '#2196F3', time: '5:00-8:00', icon: 'wb_twilight' },
  ];

  readonly streakBonuses = STREAK_BONUSES;

  readonly allBadges: BadgeInfo[] = [
    { name: 'EARLY_BIRD', displayName: 'Early Bird', description: '5+ early morning sessions in 7 days', icon: '🐦', isPermanent: false },
    { name: 'NIGHT_OWL', displayName: 'Night Owl', description: '5+ sessions after 8 PM in 7 days', icon: '🦉', isPermanent: false },
    { name: 'WEEKEND_WARRIOR', displayName: 'Weekend Warrior', description: 'Worked 4+ consecutive weekends', icon: '⚔️', isPermanent: false },
    { name: 'MARATHON_RUNNER', displayName: 'Marathon Runner', description: 'Completed a 3+ hour session', icon: '🏃', isPermanent: false },
    { name: 'CENTURION', displayName: 'Centurion', description: '100 total sessions completed', icon: '💯', isPermanent: true },
    { name: 'POINT_MASTER_100', displayName: 'Point Collector', description: 'Reached 100 points', icon: '⭐', isPermanent: true },
    { name: 'POINT_MASTER_500', displayName: 'Point Expert', description: 'Reached 500 points', icon: '🌟', isPermanent: true },
    { name: 'POINT_MASTER_1000', displayName: 'Point Master', description: 'Reached 1000 points', icon: '🌟🌟', isPermanent: true },
    { name: 'WEEK_STREAK', displayName: 'Week Streak', description: '7+ day streak', icon: '🔥', isPermanent: false },
    { name: 'MONTH_STREAK', displayName: 'Month Streak', description: '30+ day streak', icon: '🔥🔥', isPermanent: false },
    { name: 'CONSISTENT', displayName: 'Consistent', description: 'Same productive session type 5 days in a row', icon: '📅', isPermanent: false },
    { name: 'DIVERSIFIED', displayName: 'Diversified', description: 'All 3 session types in one day', icon: '🎨', isPermanent: false },
  ];

  ngOnInit(): void {
    this.api.getBadges().subscribe({
      next: (res) => {
        this.earnedBadges.set(res.badges);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  isEarned(badgeName: string): boolean {
    return this.earnedBadges().some(b => b.name === badgeName);
  }
}
