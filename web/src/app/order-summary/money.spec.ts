import { MoneyPipe } from './money';
import { RatePipe } from './rate';

describe('MoneyPipe', () => {
  const money = new MoneyPipe();

  it('agrupa los miles sin tocar los decimales', () => {
    expect(money.transform('73542')).toBe('$73.542');
    expect(money.transform('1234567.05')).toBe('$1.234.567,05');
    expect(money.transform('-1400')).toBe('-$1.400');
    expect(money.transform('999')).toBe('$999');
  });
});

describe('RatePipe', () => {
  const rate = new RatePipe();

  it('muestra la tasa como porcentaje', () => {
    expect(rate.transform('0.19')).toBe('19 %');
    expect(rate.transform('0.05')).toBe('5 %');
    expect(rate.transform('0')).toBe('0 %');
    expect(rate.transform('0.125')).toBe('12,5 %');
  });
});
