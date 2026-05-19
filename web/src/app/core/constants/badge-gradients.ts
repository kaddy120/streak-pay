export const BADGE_GRADIENTS: Record<string, string[]> = {
  WEEK_STREAK: ['#FF6B35', '#D32F2F'],
  MONTH_STREAK: ['#FF6B35', '#D32F2F'],
  EARLY_BIRD: ['#FF9A8B', '#4FC3F7'],
  NIGHT_OWL: ['#7C4DFF', '#303F9F'],
  WEEKEND_WARRIOR: ['#E53935', '#8B0000'],
  MARATHON_RUNNER: ['#00897B', '#00E5FF'],
  CENTURION: ['#FFD700', '#FF8F00'],
  POINT_MASTER_100: ['#FFEB3B', '#FFD700'],
  POINT_MASTER_500: ['#FFD700', '#FF9800'],
  POINT_MASTER_1000: ['#FFE082', '#FF6F00'],
  CONSISTENT: ['#66BB6A', '#00C853'],
  DIVERSIFIED: ['#FF6B6B', '#FFE66D', '#4ECDC4'],
};

export const DEFAULT_GRADIENT = ['#9E9E9E', '#616161'];

export function getBadgeGradient(badgeName: string): string {
  const colors = BADGE_GRADIENTS[badgeName] || DEFAULT_GRADIENT;
  return `linear-gradient(135deg, ${colors.join(', ')})`;
}
