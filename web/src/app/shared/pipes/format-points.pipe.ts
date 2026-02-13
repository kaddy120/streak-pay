import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'formatPoints', standalone: true })
export class FormatPointsPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value == null) return '0.00';
    return value.toFixed(2);
  }
}
