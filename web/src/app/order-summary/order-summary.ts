import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { Component, computed, inject } from '@angular/core';
import { ProductImage } from '../ui/product-image';
import { ProductName } from '../ui/product-name';
import { SafeAreaLayout } from '../ui/safe-area-layout';
import { MoneyPipe } from './money';
import { Line, OrderBreakdown } from './order-breakdown';
import { IMAGES, ORDER } from './order-fixture';
import { ORDER_LOOKUP, OrderLookup } from './order-lookup';
import { RatePipe } from './rate';

const FALLBACK_IMAGE = 'images/producto.svg';
const UNREACHABLE = [0, 502, 503, 504];

function hasIdentity(lookup: OrderLookup): boolean {
  return lookup.tenantId !== '' && lookup.customerId !== '';
}

function reason(error: HttpErrorResponse): string {
  if (error.error?.detail) {
    return error.error.detail;
  }
  if (UNREACHABLE.includes(error.status)) {
    return 'No se pudo conectar con la API.';
  }
  return `La API rechazó la consulta (${error.status}).`;
}

/**
 * Resumen de una orden. Con una orden en la URL la pide a la API, y sin ella muestra un desglose
 * fijo de ejemplo.
 */
@Component({
  selector: 'app-order-summary',
  imports: [ProductImage, ProductName, SafeAreaLayout, MoneyPipe, RatePipe],
  templateUrl: './order-summary.html',
})
export class OrderSummary {
  protected readonly lookup = inject(ORDER_LOOKUP);

  private readonly remote = httpResource<OrderBreakdown>(() => {
    const lookup = this.lookup;
    if (!lookup || !hasIdentity(lookup)) {
      return undefined;
    }
    return {
      url: `/v1/orders/${encodeURIComponent(lookup.orderId)}`,
      headers: { 'X-Tenant-Id': lookup.tenantId, 'X-Customer-Id': lookup.customerId },
    };
  });

  protected readonly order = computed(() => {
    if (!this.lookup) {
      return ORDER;
    }
    return this.remote.hasValue() ? this.remote.value() : undefined;
  });

  protected readonly failure = computed(() => {
    if (this.lookup && !hasIdentity(this.lookup)) {
      return 'Faltan el tenant y el tendero en la URL (?tenant=…&customer=…).';
    }
    const error = this.remote.error();
    if (!error) {
      return null;
    }
    return error instanceof HttpErrorResponse ? reason(error) : 'No se pudo leer la orden.';
  });

  private readonly money = new MoneyPipe();
  private readonly names = computed(
    () => new Map((this.order()?.lines ?? []).map((line) => [line.sku, line.name])),
  );

  protected imageOf(sku: string): string {
    return IMAGES[sku] ?? FALLBACK_IMAGE;
  }

  protected nameOf(sku: string): string {
    return this.names().get(sku) ?? sku;
  }

  protected count(value: number, one: string, many: string): string {
    return `${value} ${value === 1 ? one : many}`;
  }

  protected comboNote(line: Line): string {
    const parts = line.taxBaseAllocations.map((part) => this.money.transform(part.amount));
    return parts.length > 0 ? ` (descuenta ${parts.join(' y ')} del combo)` : '';
  }
}
