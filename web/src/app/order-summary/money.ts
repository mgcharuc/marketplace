import { Pipe, PipeTransform } from '@angular/core';

/**
 * Muestra un monto que llega como texto con el signo $, el punto en los miles y la coma en los
 * decimales, sin pasarlo a número.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: string): string {
    const negative = amount.startsWith('-');
    const [integer, decimals] = amount.replace('-', '').split('.');
    const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return `${negative ? '-' : ''}$${grouped}${decimals ? ',' + decimals : ''}`;
  }
}
