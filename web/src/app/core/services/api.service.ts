import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  SessionResponse, CreateSessionRequest, UpdateSessionRequest,
  TodayStatsResponse, PointsResponse,
  DashboardResponse,
  BadgesResponse,
  StreakResponse,
  AppSettingsResponse, UpdateSettingsRequest, DailyGoalResponse, UpdateGoalRequest,
  WishItemResponse, CreateWishItemRequest, UpdateWishItemRequest,
  DeviceStatusResponse, DaemonCommandRequest, ImageUploadResponse,
} from '../models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  // Dashboard
  getDashboard(): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(`${this.base}/dashboard`);
  }

  // Sessions
  createSession(req: CreateSessionRequest): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`${this.base}/sessions`, req);
  }

  updateSession(id: number, req: UpdateSessionRequest): Observable<SessionResponse> {
    return this.http.put<SessionResponse>(`${this.base}/sessions/${id}`, req);
  }

  getSession(id: number): Observable<SessionResponse> {
    return this.http.get<SessionResponse>(`${this.base}/sessions/${id}`);
  }

  getSessions(startDate?: string, endDate?: string): Observable<SessionResponse[]> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<SessionResponse[]>(`${this.base}/sessions`, { params });
  }

  deleteSession(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/sessions/${id}`);
  }

  getActiveSessions(deviceId?: string): Observable<SessionResponse[]> {
    let params = new HttpParams();
    if (deviceId) params = params.set('deviceId', deviceId);
    return this.http.get<SessionResponse[]>(`${this.base}/sessions/active`, { params });
  }

  getTodayStats(): Observable<TodayStatsResponse> {
    return this.http.get<TodayStatsResponse>(`${this.base}/stats/today`);
  }

  getTotalPoints(): Observable<PointsResponse> {
    return this.http.get<PointsResponse>(`${this.base}/stats/points`);
  }

  // Streak
  getStreakInfo(): Observable<StreakResponse> {
    return this.http.get<StreakResponse>(`${this.base}/stats/streak`);
  }

  // Badges
  getBadges(): Observable<BadgesResponse> {
    return this.http.get<BadgesResponse>(`${this.base}/badges`);
  }

  // Settings
  getSettings(): Observable<AppSettingsResponse> {
    return this.http.get<AppSettingsResponse>(`${this.base}/settings`);
  }

  updateSettings(req: UpdateSettingsRequest): Observable<AppSettingsResponse> {
    return this.http.put<AppSettingsResponse>(`${this.base}/settings`, req);
  }

  // Goals
  getGoals(): Observable<DailyGoalResponse> {
    return this.http.get<DailyGoalResponse>(`${this.base}/goals`);
  }

  updateGoals(req: UpdateGoalRequest): Observable<DailyGoalResponse> {
    return this.http.put<DailyGoalResponse>(`${this.base}/goals`, req);
  }

  // Wish Items
  getWishItems(filter?: 'available' | 'redeemed'): Observable<WishItemResponse[]> {
    let params = new HttpParams();
    if (filter) params = params.set('filter', filter);
    return this.http.get<WishItemResponse[]>(`${this.base}/wishlist`, { params });
  }

  createWishItem(req: CreateWishItemRequest): Observable<WishItemResponse> {
    return this.http.post<WishItemResponse>(`${this.base}/wishlist`, req);
  }

  updateWishItem(id: number, req: UpdateWishItemRequest): Observable<WishItemResponse> {
    return this.http.put<WishItemResponse>(`${this.base}/wishlist/${id}`, req);
  }

  deleteWishItem(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/wishlist/${id}`);
  }

  // Devices
  getDeviceStatuses(): Observable<DeviceStatusResponse[]> {
    return this.http.get<DeviceStatusResponse[]>(`${this.base}/devices/status`);
  }

  sendCommand(req: DaemonCommandRequest): Observable<unknown> {
    return this.http.post(`${this.base}/devices/command`, req);
  }

  // Images
  uploadImage(file: File): Observable<ImageUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImageUploadResponse>(`${this.base}/images`, formData);
  }

  getImageUrl(path: string): string {
    if (!path) return '';
    if (path.startsWith('http')) return path;
    return `${this.base}/images/${path}`;
  }
}
