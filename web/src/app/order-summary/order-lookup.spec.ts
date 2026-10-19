import { lookupFrom } from './order-lookup';

describe('lookupFrom', () => {
  it('lee la orden y el tendero de la URL', () => {
    expect(lookupFrom('?orderId=o_1&tenant=CO-MARCA-A&customer=CLI-CONTADO')).toEqual({
      orderId: 'o_1',
      tenantId: 'CO-MARCA-A',
      customerId: 'CLI-CONTADO',
    });
  });

  it('devuelve null si la URL no trae orderId', () => {
    expect(lookupFrom('')).toBeNull();
    expect(lookupFrom('?tenant=CO-MARCA-A')).toBeNull();
  });
});
