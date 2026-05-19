import { Injectable, signal, OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { environment } from '../../../environments/environment';
import { SessionResponse, WishItemResponse, StreakResponse } from '../models';

export type ConnectionState = 'connected' | 'disconnected' | 'reconnecting';

export interface SseSessionEvent {
  type: 'created' | 'updated';
  session: SessionResponse;
}

export interface SseDeleteEvent {
  id: number;
}

export interface SseStatsEvent {
  totalPoints: number;
  streak: StreakResponse;
}

@Injectable({ providedIn: 'root' })
export class SseService implements OnDestroy {
  private clientId = `web-${crypto.randomUUID()}`;
  private abortController: AbortController | null = null;
  private retryCount = 0;

  connectionState = signal<ConnectionState>('disconnected');

  // Event subjects
  readonly sessionCreated$ = new Subject<SessionResponse>();
  readonly sessionUpdated$ = new Subject<SessionResponse>();
  readonly sessionDeleted$ = new Subject<number>();
  readonly statsUpdated$ = new Subject<SseStatsEvent>();
  readonly wishItemCreated$ = new Subject<WishItemResponse>();
  readonly wishItemUpdated$ = new Subject<WishItemResponse>();
  readonly wishItemDeleted$ = new Subject<number>();
  readonly settingsUpdated$ = new Subject<void>();
  readonly heartbeat$ = new Subject<void>();

  connect(): void {
    if (this.abortController) return;
    this.startConnection();
  }

  disconnect(): void {
    this.abortController?.abort();
    this.abortController = null;
    this.connectionState.set('disconnected');
  }

  private async startConnection(): Promise<void> {
    this.abortController = new AbortController();
    const url = `${environment.apiBaseUrl}/events?clientId=${this.clientId}`;

    try {
      await fetchEventSource(url, {
        headers: { 'X-API-Key': environment.apiKey },
        signal: this.abortController.signal,
        onopen: async () => {
          this.connectionState.set('connected');
          this.retryCount = 0;
        },
        onmessage: (event) => {
          if (!event.data) return;
          this.parseEvent(event.event, event.data);
        },
        onerror: () => {
          this.connectionState.set('reconnecting');
          this.retryCount++;
          const delay = Math.min(1000 * Math.pow(2, this.retryCount), 30000);
          return delay;
        },
        onclose: () => {
          this.connectionState.set('disconnected');
        },
      });
    } catch {
      // Connection was aborted or failed
      this.connectionState.set('disconnected');
    }
  }

  private parseEvent(eventType: string, data: string): void {
    try {
      const parsed = JSON.parse(data);
      switch (eventType) {
        case 'session.created':
          this.sessionCreated$.next(parsed as SessionResponse);
          break;
        case 'session.updated':
          this.sessionUpdated$.next(parsed as SessionResponse);
          break;
        case 'session.deleted':
          this.sessionDeleted$.next(parsed.id);
          break;
        case 'stats.updated':
          this.statsUpdated$.next({
            totalPoints: parsed.totalPoints,
            streak: parsed.streak,
          });
          break;
        case 'wishitem.created':
          this.wishItemCreated$.next(parsed as WishItemResponse);
          break;
        case 'wishitem.updated':
          this.wishItemUpdated$.next(parsed as WishItemResponse);
          break;
        case 'wishitem.deleted':
          this.wishItemDeleted$.next(parsed.id);
          break;
        case 'settings.updated':
          this.settingsUpdated$.next();
          break;
        case 'heartbeat':
          this.heartbeat$.next();
          break;
      }
    } catch {
      // Ignore parse errors for unknown event types
    }
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
