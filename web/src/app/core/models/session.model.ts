export enum SessionType {
  DAY_JOB = 'DAY_JOB',
  SIDE_WORK = 'SIDE_WORK',
  EARLY_MORNING = 'EARLY_MORNING',
}

export interface CreateSessionRequest {
  deviceId: string;
  startTime: string; // ISO-8601
  type: SessionType;
}

export interface UpdateSessionRequest {
  startTime?: string;
  endTime?: string;
  isPaused?: boolean;
  pausedAt?: string;
  totalPausedSeconds?: number;
  durationMinutes?: number;
}

export interface SessionResponse {
  id: number;
  deviceId: string;
  startTime: string;
  endTime: string | null;
  durationMinutes: number;
  pointsEarned: number;
  type: SessionType;
  isPaused: boolean;
  pausedAt: string | null;
  totalPausedSeconds: number;
  activeElapsedSeconds: number;
}

export interface TodayStatsResponse {
  totalMinutes: number;
  totalPoints: number;
  sessionCount: number;
  dayJobMinutes: number;
  sideWorkMinutes: number;
  earlyMorningMinutes: number;
}

export interface PointsResponse {
  totalPoints: number;
}
