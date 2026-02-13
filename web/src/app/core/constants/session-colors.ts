import { SessionType } from '../models';

export const SESSION_COLORS: Record<SessionType, string> = {
  [SessionType.DAY_JOB]: '#F97316',
  [SessionType.SIDE_WORK]: '#22C55E',
  [SessionType.EARLY_MORNING]: '#38BDF8',
};

export const SESSION_LABELS: Record<SessionType, string> = {
  [SessionType.DAY_JOB]: 'Day Job',
  [SessionType.SIDE_WORK]: 'Side Work',
  [SessionType.EARLY_MORNING]: 'Early Morning',
};

export const SESSION_ICONS: Record<SessionType, string> = {
  [SessionType.DAY_JOB]: 'work',
  [SessionType.SIDE_WORK]: 'code',
  [SessionType.EARLY_MORNING]: 'wb_twilight',
};
