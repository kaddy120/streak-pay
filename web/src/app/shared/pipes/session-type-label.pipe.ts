import { Pipe, PipeTransform } from '@angular/core';
import { SESSION_LABELS } from '../../core/constants/session-colors';
import { SessionType } from '../../core/models';

@Pipe({ name: 'sessionTypeLabel', standalone: true })
export class SessionTypeLabelPipe implements PipeTransform {
  transform(value: SessionType | string | null | undefined): string {
    if (!value) return '';
    return SESSION_LABELS[value as SessionType] || value;
  }
}
