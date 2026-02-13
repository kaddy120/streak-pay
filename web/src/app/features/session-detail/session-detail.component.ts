import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ApiService } from '../../core/services/api.service';
import { FormatService } from '../../core/services/format.service';
import { SessionResponse, SessionType } from '../../core/models';
import { SESSION_COLORS, SESSION_LABELS, SESSION_ICONS } from '../../core/constants/session-colors';
import { FormatPointsPipe } from '../../shared/pipes/format-points.pipe';
import { FormatDurationPipe } from '../../shared/pipes/format-duration.pipe';
import { SessionTypeLabelPipe } from '../../shared/pipes/session-type-label.pipe';

@Component({
  selector: 'app-session-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatCardModule, MatButtonModule,
    MatFormFieldModule, MatInputModule, MatSnackBarModule,
    FormatPointsPipe, FormatDurationPipe, SessionTypeLabelPipe,
  ],
  templateUrl: './session-detail.component.html',
  styleUrl: './session-detail.component.scss',
})
export class SessionDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private api = inject(ApiService);
  private snackBar = inject(MatSnackBar);
  fmt = inject(FormatService);

  session = signal<SessionResponse | null>(null);
  loading = signal(true);

  // Edit state
  editStartDate = '';
  editStartTime = '';
  editEndDate = '';
  editEndTime = '';

  readonly SESSION_COLORS = SESSION_COLORS;
  readonly SESSION_LABELS = SESSION_LABELS;
  readonly SESSION_ICONS = SESSION_ICONS;

  isActive = computed(() => {
    const s = this.session();
    return s != null && s.endTime == null;
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.getSession(id).subscribe({
      next: (session) => {
        this.session.set(session);
        this.syncEditFields(session);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
    });
  }

  private syncEditFields(s: SessionResponse): void {
    if (s.startTime) {
      const [d, t] = s.startTime.split('T');
      this.editStartDate = d;
      this.editStartTime = t?.substring(0, 5) || '';
    }
    if (s.endTime) {
      const [d, t] = s.endTime.split('T');
      this.editEndDate = d;
      this.editEndTime = t?.substring(0, 5) || '';
    }
  }

  getSessionColor(type: SessionType): string {
    return SESSION_COLORS[type];
  }

  getSessionIcon(type: SessionType): string {
    return SESSION_ICONS[type];
  }

  save(): void {
    const s = this.session();
    if (!s) return;

    const startTime = `${this.editStartDate}T${this.editStartTime}:00`;
    const endTime = this.editEndDate && this.editEndTime
      ? `${this.editEndDate}T${this.editEndTime}:00`
      : undefined;

    this.api.updateSession(s.id, { startTime, endTime }).subscribe({
      next: (updated) => {
        this.session.set(updated);
        this.syncEditFields(updated);
        this.snackBar.open('Session updated', 'OK', { duration: 2000 });
      },
    });
  }

  stopSession(): void {
    const s = this.session();
    if (!s) return;
    this.api.updateSession(s.id, { endTime: new Date().toISOString().replace('Z', '') }).subscribe({
      next: (updated) => {
        this.session.set(updated);
        this.syncEditFields(updated);
        this.snackBar.open('Session stopped', 'OK', { duration: 2000 });
      },
    });
  }

  resumeSession(): void {
    const s = this.session();
    if (!s) return;
    this.api.updateSession(s.id, { isPaused: false, endTime: undefined }).subscribe({
      next: (updated) => {
        this.session.set(updated);
        this.snackBar.open('Session resumed', 'OK', { duration: 2000 });
      },
    });
  }

  deleteSession(): void {
    const s = this.session();
    if (!s) return;
    this.api.deleteSession(s.id).subscribe({
      next: () => {
        this.snackBar.open('Session deleted', 'OK', { duration: 2000 });
        this.router.navigate(['/dashboard']);
      },
    });
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }
}
