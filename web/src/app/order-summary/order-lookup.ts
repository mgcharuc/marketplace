import { InjectionToken } from '@angular/core';

/** Orden confirmada que se pide a la API, con el tendero que la consulta. */
export interface OrderLookup {
  orderId: string;
  tenantId: string;
  customerId: string;
}

/** Lee la orden de la URL (`?orderId=…&tenant=…&customer=…`). Devuelve null si falta `orderId`. */
export function lookupFrom(search: string): OrderLookup | null {
  const params = new URLSearchParams(search);
  const orderId = params.get('orderId');
  if (!orderId) {
    return null;
  }
  return {
    orderId,
    tenantId: params.get('tenant') ?? '',
    customerId: params.get('customer') ?? '',
  };
}

export const ORDER_LOOKUP = new InjectionToken<OrderLookup | null>('ORDER_LOOKUP', {
  providedIn: 'root',
  factory: () => lookupFrom(globalThis.location?.search ?? ''),
});
