import { Pipe, PipeTransform } from '@angular/core';
import { CURRENCY_TO_POINTS_RATE } from '../../core/constants/points.constants';

@Pipe({ name: 'formatPrice', standalone: true })
export class FormatPricePipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value == null) return 'R0.00';
    return `R${value.toFixed(2)}`;
  }
}

@Pipe({ name: 'priceToPoints', standalone: true })
export class PriceToPointsPipe implements PipeTransform {
  transform(price: number | null | undefined): string {
    if (price == null) return '0.0 pts';
    return `${(price / CURRENCY_TO_POINTS_RATE).toFixed(1)} pts`;
  }
}
