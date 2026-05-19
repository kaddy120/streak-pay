import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ApiService } from '../../core/services/api.service';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatCardModule, MatButtonModule,
    MatFormFieldModule, MatInputModule, MatSlideToggleModule, MatSnackBarModule,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  private api = inject(ApiService);
  private snackBar = inject(MatSnackBar);
  theme = inject(ThemeService);

  userName = '';
  dayJobHours = 8;
  sideWorkHours = 2;
  loading = signal(true);

  ngOnInit(): void {
    this.api.getSettings().subscribe({
      next: (s) => {
        this.userName = s.userName;
        this.loading.set(false);
      },
    });
    this.api.getGoals().subscribe({
      next: (g) => {
        this.dayJobHours = g.dayJobHours;
        this.sideWorkHours = g.sideWorkHours;
      },
    });
  }

  saveUserName(): void {
    this.api.updateSettings({ userName: this.userName }).subscribe({
      next: () => this.snackBar.open('Name updated', 'OK', { duration: 2000 }),
    });
  }

  saveGoals(): void {
    this.api.updateGoals({ dayJobHours: this.dayJobHours, sideWorkHours: this.sideWorkHours }).subscribe({
      next: () => this.snackBar.open('Goals updated', 'OK', { duration: 2000 }),
    });
  }
}
