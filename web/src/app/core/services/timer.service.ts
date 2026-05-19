import { Injectable, signal, computed } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class TimerService {
  private intervalId: ReturnType<typeof setInterval> | null = null;

  readonly elapsed = signal(0);
  readonly running = signal(false);
  readonly paused = signal(false);

  readonly formattedElapsed = computed(() => {
    const s = this.elapsed();
    const hours = Math.floor(s / 3600);
    const minutes = Math.floor((s % 3600) / 60);
    const secs = s % 60;
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  });

  start(initialSeconds = 0): void {
    this.stop();
    this.elapsed.set(initialSeconds);
    this.running.set(true);
    this.paused.set(false);
    this.intervalId = setInterval(() => {
      this.elapsed.update(v => v + 1);
    }, 1000);
  }

  pause(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    this.paused.set(true);
  }

  resume(): void {
    if (!this.running()) return;
    this.paused.set(false);
    this.intervalId = setInterval(() => {
      this.elapsed.update(v => v + 1);
    }, 1000);
  }

  stop(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    this.elapsed.set(0);
    this.running.set(false);
    this.paused.set(false);
  }

  syncElapsed(seconds: number): void {
    this.elapsed.set(seconds);
  }
}
