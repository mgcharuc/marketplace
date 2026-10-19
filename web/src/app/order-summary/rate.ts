import { Pipe, PipeTransform } from '@angular/core';

/** Muestra una tasa como porcentaje ("0.19" pasa a "19 %") moviendo la coma, sin pasarla a número. */
@Pipe({ name: 'rate' })
export class RatePipe implements PipeTransform {
  transform(rate: string): string {
    const [integer, decimals = ''] = rate.split('.');
    const digits = decimals.padEnd(2, '0');
    const whole = (integer + digits.slice(0, 2)).replace(/^0+(?=\d)/, '');
    const rest = digits.slice(2);
    return rest ? `${whole},${rest} %` : `${whole} %`;
  }
}
