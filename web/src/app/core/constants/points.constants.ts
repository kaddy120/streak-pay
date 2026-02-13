export const CURRENCY_TO_POINTS_RATE = 9.5;
export const MIN_SESSION_DURATION_MINUTES = 15;

export const POINT_RATES = {
  DAY_JOB: 0.25,
  SIDE_WORK: 1.0,
  EARLY_MORNING: 1.5,
  FIRST_HOUR_BONUS: 0.5,
};

export const STREAK_BONUSES: { minDays: number; percentage: number }[] = [
  { minDays: 30, percentage: 20 },
  { minDays: 7, percentage: 15 },
  { minDays: 3, percentage: 10 },
];

export function getStreakBonusPercentage(streakDays: number): number {
  for (const bonus of STREAK_BONUSES) {
    if (streakDays >= bonus.minDays) return bonus.percentage;
  }
  return 0;
}
