export interface AppSettingsResponse {
  userName: string;
  currentStreak: number;
  lastWorkDate: string | null;
  lastSessionEndTime: string | null;
  consecutiveWorkDays: number;
}

export interface UpdateSettingsRequest {
  userName?: string;
}

export interface DailyGoalResponse {
  dayJobHours: number;
  sideWorkHours: number;
}

export interface UpdateGoalRequest {
  dayJobHours?: number;
  sideWorkHours?: number;
}
