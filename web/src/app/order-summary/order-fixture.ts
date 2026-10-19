import { OrderBreakdown } from './order-breakdown';

// Montos de las semillas de CO-MARCA-A con GASEOSA-350 × 25, GASEOSA-1500 × 2 y SNACK-PAPAS × 3.
// Los nombres se alargaron para probar textos de más de 80 caracteres.
export const ORDER: OrderBreakdown = {
  currency: { code: 'COP', scale: 0 },
  lines: [
    {
      lineId: 'L1',
      sku: 'GASEOSA-350',
      name: 'Gaseosa Cola sabor original en lata de 350 ml, paquete promocional para tiendas de barrio y minimercados',
      quantity: 25,
      unitPrice: '2000',
      grossAmount: '50000',
      scaleDiscounts: [{ ruleId: 'ESC-GAS350', amount: '2200' }],
      taxBaseAllocations: [],
      taxableBase: '47800',
      taxRate: '0.19',
      taxAmount: '9082',
      netAmount: '56882',
    },
    {
      lineId: 'L2',
      sku: 'GASEOSA-1500',
      name: 'Gaseosa Cola sabor original en botella retornable de 1,5 litros con tapa rosca, caja por unidad',
      quantity: 2,
      unitPrice: '5000',
      grossAmount: '10000',
      scaleDiscounts: [],
      taxBaseAllocations: [{ ruleId: 'CMB-FIESTA', amount: '909' }],
      taxableBase: '9091',
      taxRate: '0.19',
      taxAmount: '1727',
      netAmount: '10818',
    },
    {
      lineId: 'L3',
      sku: 'SNACK-PAPAS',
      name: 'Papas fritas onduladas sabor natural con sal marina, bolsa familiar de 150 gramos edición limitada',
      quantity: 3,
      unitPrice: '1800',
      grossAmount: '5400',
      scaleDiscounts: [],
      taxBaseAllocations: [{ ruleId: 'CMB-FIESTA', amount: '491' }],
      taxableBase: '4909',
      taxRate: '0.19',
      taxAmount: '933',
      netAmount: '5842',
    },
  ],
  discounts: [
    {
      ruleId: 'CMB-FIESTA',
      type: 'COMBO',
      name: 'Combo Fiesta',
      amount: '1400',
      components: [
        { sku: 'GASEOSA-1500', qty: 2 },
        { sku: 'SNACK-PAPAS', qty: 3 },
      ],
    },
    {
      ruleId: 'ESC-GAS350',
      type: 'ESCALA',
      name: 'Escala Gaseosa 350',
      amount: '2200',
      components: null,
    },
  ],
  gifts: [
    {
      ruleId: 'OBS-JUGO',
      sku: 'JUGO-1L',
      name: 'Jugo de naranja natural sin azúcar añadida en caja tetra pak de 1 litro, obsequio por compra de gaseosas',
      quantity: 2,
      taxAmount: '400',
      taxBorneBy: 'SELLER',
    },
  ],
  totals: {
    grossSubtotal: '65400',
    discountTotal: '3600',
    netSubtotal: '61800',
    taxableBase: '61800',
    taxTotal: '11742',
    total: '73542',
  },
};

// La API no trae imágenes. Cada una tiene otra proporción para probar el grid.
export const IMAGES: Record<string, string> = {
  'GASEOSA-350': 'images/gaseosa-350.svg',
  'GASEOSA-1500': 'images/gaseosa-1500.svg',
  'SNACK-PAPAS': 'images/snack-papas.svg',
  'JUGO-1L': 'images/jugo-1l.svg',
};
