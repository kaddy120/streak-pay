export interface DeviceHeartbeatRequest {
  deviceId: string;
  state: string;
  currentApp?: string;
  sessionId?: number;
  elapsedSeconds: number;
  pausedSeconds: number;
}

export interface DeviceStatusResponse {
  deviceId: string;
  state: string;
  currentApp: string | null;
  sessionId: number | null;
  elapsedSeconds: number;
  pausedSeconds: number;
  lastHeartbeat: string;
}

export interface DaemonCommandRequest {
  deviceId: string;
  action: string;
}

export interface ImageUploadResponse {
  url: string;
}
