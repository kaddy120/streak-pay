import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { CURRENCY_TO_POINTS_RATE } from '../../core/constants/points.constants';

@Component({
  selector: 'app-add-wish-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Add Wish Item</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Name</mat-label>
        <input matInput [(ngModel)]="name" placeholder="e.g., New headphones">
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Price (ZAR)</mat-label>
        <input matInput type="number" [(ngModel)]="price" placeholder="0.00" min="0">
      </mat-form-field>

      @if (price > 0) {
        <p class="points-preview">
          = <strong>{{ (price / rate).toFixed(1) }} pts</strong> required
        </p>
      }

      <div class="image-upload">
        <input type="file" accept="image/*" (change)="onFileSelect($event)" #fileInput hidden>
        <button mat-stroked-button (click)="fileInput.click()" class="upload-btn">
          <span class="material-symbols-rounded">upload</span>
          {{ imageUrl ? 'Change Image' : 'Add Image' }}
        </button>
        @if (imageUrl) {
          <img [src]="imagePreview" class="image-preview" alt="preview">
        }
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button [disabled]="!name || price <= 0 || saving()" (click)="save()">
        {{ saving() ? 'Saving...' : 'Add' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    .points-preview {
      font-size: 14px;
      color: var(--color-accent);
      margin: -8px 0 16px;
    }
    .image-upload {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: 8px;
    }
    .upload-btn {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .image-preview {
      width: 60px;
      height: 60px;
      border-radius: 8px;
      object-fit: cover;
    }
  `],
})
export class AddWishDialogComponent {
  private api = inject(ApiService);
  private dialogRef = inject(MatDialogRef<AddWishDialogComponent>);

  name = '';
  price = 0;
  imageUrl = '';
  imagePreview = '';
  saving = signal(false);
  rate = CURRENCY_TO_POINTS_RATE;

  onFileSelect(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.imagePreview = URL.createObjectURL(file);
    this.api.uploadImage(file).subscribe({
      next: (res) => this.imageUrl = res.url,
    });
  }

  save(): void {
    this.saving.set(true);
    this.api.createWishItem({
      name: this.name,
      price: this.price,
      imageUrl: this.imageUrl || undefined,
    }).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => this.saving.set(false),
    });
  }
}
