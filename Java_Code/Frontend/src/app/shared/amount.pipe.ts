import { Pipe, PipeTransform } from '@angular/core';

/**
 * Money formatting for screen display. Values arrive as exact decimals from PostgreSQL and are
 * rendered with two decimal places and thousands separators, never as binary floating point text.
 */
@Pipe({ name: 'cdAmount' })
export class AmountPipe implements PipeTransform {
  transform(value: number | string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return '';
    }
    const numeric = typeof value === 'string' ? Number(value) : value;
    if (Number.isNaN(numeric)) {
      return String(value);
    }
    return numeric.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }
}
