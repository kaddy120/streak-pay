import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ApiService } from '../../core/services/api.service';
import { FormatService } from '../../core/services/format.service';
import { WishItemResponse } from '../../core/models';
import { FormatPointsPipe } from '../../shared/pipes/format-points.pipe';
import { FormatPricePipe } from '../../shared/pipes/format-price.pipe';
import { CURRENCY_TO_POINTS_RATE } from '../../core/constants/points.constants';
import { AddWishDialogComponent } from './add-wish-dialog.component';

@Component({
  selector: 'app-wishlist',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatCardModule, MatTabsModule,
    MatButtonModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatSnackBarModule,
    FormatPointsPipe, FormatPricePipe,
  ],
  templateUrl: './wishlist.component.html',
  styleUrl: './wishlist.component.scss',
})
export class WishlistComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  fmt = inject(FormatService);

  availableItems = signal<WishItemResponse[]>([]);
  redeemedItems = signal<WishItemResponse[]>([]);
  totalPoints = signal(0);
  loading = signal(true);
  selectedTab = signal(0);

  ngOnInit(): void {
    this.loadData();
  }

  private loadData(): void {
    this.api.getTotalPoints().subscribe({
      next: (res) => this.totalPoints.set(res.totalPoints),
    });
    this.api.getWishItems().subscribe({
      next: (items) => {
        this.availableItems.set(items.filter(i => !i.isRedeemed));
        this.redeemedItems.set(items.filter(i => i.isRedeemed));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  openAddDialog(): void {
    const dialogRef = this.dialog.open(AddWishDialogComponent, {
      width: '440px',
      data: { totalPoints: this.totalPoints() },
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) this.loadData();
    });
  }

  goToItem(id: number): void {
    this.router.navigate(['/wishlist', id]);
  }

  canAfford(item: WishItemResponse): boolean {
    return this.totalPoints() >= item.price / CURRENCY_TO_POINTS_RATE;
  }

  getPointsRequired(price: number): number {
    return price / CURRENCY_TO_POINTS_RATE;
  }

  getImageUrl(url: string | null): string {
    if (!url) return '';
    return this.api.getImageUrl(url);
  }
}
