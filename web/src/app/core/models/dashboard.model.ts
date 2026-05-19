import { SessionResponse } from './session.model';
import { BadgesResponse } from './badge.model';
import { DailyGoalResponse } from './settings.model';
import { StreakResponse } from './streak.model';
import { DeviceStatusResponse } from './device.model';

export interface DashboardResponse {
  totalPoints: number;
  streak: StreakResponse;
  userName: string;
  recentSessions: SessionResponse[];
  badges: BadgesResponse;
  activeSessions: SessionResponse[];
  goals: DailyGoalResponse;
  deviceStatuses: DeviceStatusResponse[];
}
