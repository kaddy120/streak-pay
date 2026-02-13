export interface StreakResponse {
  currentStreak: number;
  gracePeriodHoursRemaining: number;
  gracePeriodMinutesRemaining: number;
  streakAtRisk: boolean;
  isUrgent: boolean;
}
