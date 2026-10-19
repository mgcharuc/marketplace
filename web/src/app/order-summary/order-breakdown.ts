/** Los campos del desglose de la API que usa esta pantalla. Los montos llegan como texto. */
export interface OrderBreakdown {
  orderNumber?: string;
  status?: string;
  currency: { code: string; scale: number };
  lines: Line[];
  discounts: Discount[];
  gifts: Gift[];
  totals: Totals;
}

export interface Line {
  lineId: string;
  sku: string;
  name: string;
  quantity: number;
  unitPrice: string;
  grossAmount: string;
  scaleDiscounts: { ruleId: string; amount: string }[];
  taxBaseAllocations: { ruleId: string; amount: string }[];
  taxableBase: string;
  taxRate: string;
  taxAmount: string;
  netAmount: string;
}

export interface Discount {
  ruleId: string;
  type: 'COMBO' | 'ESCALA';
  name: string;
  amount: string;
  components: { sku: string; qty: number }[] | null;
}

export interface Gift {
  ruleId: string;
  sku: string;
  name: string;
  quantity: number;
  taxAmount: string;
  taxBorneBy: 'SELLER' | 'CUSTOMER' | null;
}

export interface Totals {
  grossSubtotal: string;
  discountTotal: string;
  netSubtotal: string;
  taxableBase: string;
  taxTotal: string;
  total: string;
}
