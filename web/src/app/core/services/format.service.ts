import { Injectable } from '@angular/core';
import { CURRENCY_TO_POINTS_RATE } from '../constants/points.constants';

@Injectable({ providedIn: 'root' })
export class FormatService {
  priceToPoints(price: number): number {
    return price / CURRENCY_TO_POINTS_RATE;
  }

  formatPriceAsPoints(price: number): string {
    return `${this.priceToPoints(price).toFixed(1)} pts`;
  }

  formatPrice(price: number): string {
    return `R${price.toFixed(2)}`;
  }

  formatElapsedTime(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }

  formatDuration(minutes: number): string {
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;
  }

  formatPoints(points: number): string {
    return points.toFixed(2);
  }

  formatDateTime(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' })
      + ' ' + d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' });
  }

  formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
  }
}
