export interface BadgesResponse {
  badges: BadgeDto[];
  highlightedBadges: BadgeDto[];
  motivationalMessage: string;
}

export interface BadgeDto {
  name: string;
  displayName: string;
  description: string;
  icon: string;
  isPermanent: boolean;
}
