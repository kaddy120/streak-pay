import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ApiService } from '../../core/services/api.service';
import { FormatService } from '../../core/services/format.service';
import { WishItemResponse } from '../../core/models';
import { CURRENCY_TO_POINTS_RATE } from '../../core/constants/points.constants';
import { FormatPointsPipe } from '../../shared/pipes/format-points.pipe';
import { FormatPricePipe } from '../../shared/pipes/format-price.pipe';

@Component({
  selector: 'app-confirm-delete-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Delete item</h2>
    <mat-dialog-content>Are you sure you want to delete this item?</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">Delete</button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDeleteDialogComponent {}

@Component({
  selector: 'app-wish-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatButtonModule, MatDialogModule,
    MatFormFieldModule, MatInputModule, MatSnackBarModule,
    FormatPointsPipe, FormatPricePipe,
  ],
  templateUrl: './wish-detail.component.html',
  styleUrl: './wish-detail.component.scss',
})
export class WishDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private api = inject(ApiService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  fmt = inject(FormatService);

  item = signal<WishItemResponse | null>(null);
  totalPoints = signal(0);
  editing = signal(false);
  editName = '';
  editPrice = 0;
  loading = signal(true);

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.getWishItems().subscribe({
      next: (items) => {
        const found = items.find(i => i.id === id);
        if (found) {
          this.item.set(found);
          this.editName = found.name;
          this.editPrice = found.price;
        }
        this.loading.set(false);
      },
    });
    this.api.getTotalPoints().subscribe({
      next: (res) => this.totalPoints.set(res.totalPoints),
    });
  }

  get canAfford(): boolean {
    const i = this.item();
    if (!i) return false;
    return this.totalPoints() >= i.price / CURRENCY_TO_POINTS_RATE;
  }

  get pointsRequired(): number {
    const i = this.item();
    if (!i) return 0;
    return i.price / CURRENCY_TO_POINTS_RATE;
  }

  getImageUrl(url: string | null): string {
    if (!url) return '';
    return this.api.getImageUrl(url);
  }

  startEdit(): void {
    this.editing.set(true);
  }

  cancelEdit(): void {
    const i = this.item();
    if (i) {
      this.editName = i.name;
      this.editPrice = i.price;
    }
    this.editing.set(false);
  }

  saveEdit(): void {
    const i = this.item();
    if (!i) return;
    this.api.updateWishItem(i.id, { name: this.editName, price: this.editPrice }).subscribe({
      next: (updated) => {
        this.item.set(updated);
        this.editing.set(false);
        this.snackBar.open('Wish item updated', 'OK', { duration: 2000 });
      },
    });
  }

  redeem(): void {
    const i = this.item();
    if (!i) return;
    this.api.updateWishItem(i.id, {
      isRedeemed: true,
      redeemedDate: new Date().toISOString().split('T')[0],
    }).subscribe({
      next: () => {
        this.snackBar.open('Item redeemed!', 'OK', { duration: 2000 });
        this.router.navigate(['/wishlist']);
      },
    });
  }

  delete(): void {
    const i = this.item();
    if (!i) return;
    this.dialog.open(ConfirmDeleteDialogComponent).afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.deleteWishItem(i.id).subscribe({
        next: () => {
          this.snackBar.open('Wish item deleted', 'OK', { duration: 2000 });
          this.router.navigate(['/wishlist']);
        },
      });
    });
  }

  goBack(): void {
    this.router.navigate(['/wishlist']);
  }
}
