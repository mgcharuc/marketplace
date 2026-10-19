import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrderSummary } from './order-summary';
import { MoneyPipe } from './money';
import { ORDER } from './order-fixture';
import { ORDER_LOOKUP, OrderLookup } from './order-lookup';

describe('OrderSummary', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => http().verify());

  function http(): HttpTestingController {
    return TestBed.inject(HttpTestingController);
  }

  function render(): HTMLElement {
    const fixture = TestBed.createComponent(OrderSummary);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('muestra completos los nombres de más de 80 caracteres', () => {
    const names = [...render().querySelectorAll('#lines-title + ul app-product-name')].map(
      (element) => element.textContent,
    );

    expect(ORDER.lines.every((line) => line.name.length > 80)).toBe(true);
    expect(names).toEqual(ORDER.lines.map((line) => line.name));
  });

  it('muestra la base, el total y el descuento de escala de cada línea', () => {
    const money = new MoneyPipe();
    const lines = [...render().querySelectorAll('#lines-title + ul > li')];

    expect(lines).toHaveLength(ORDER.lines.length);
    ORDER.lines.forEach((line, index) => {
      expect(lines[index].textContent).toContain(`Base ${money.transform(line.taxableBase)}`);
      expect(lines[index].textContent).toContain(`Total ${money.transform(line.netAmount)}`);
    });
    expect(lines[0].textContent).toContain('Escala -$2.200');
    expect(lines[1].textContent).toContain('descuenta $909 del combo');
  });

  it('muestra el combo como un grupo con sus productos y la escala aparte', () => {
    const discounts = [
      ...render().querySelectorAll('[aria-labelledby="discounts-title"] > ul > li'),
    ];

    expect(discounts).toHaveLength(2);
    expect(discounts[0].textContent).toContain('Precio de combo');
    expect(discounts[0].textContent).toContain('Combo Fiesta');
    expect(discounts[0].querySelectorAll('ul li')).toHaveLength(2);
    expect(discounts[1].textContent).toContain('Descuento por cantidad');
    expect(discounts[1].textContent).toContain('Escala Gaseosa 350');
  });

  it('muestra el obsequio con su cantidad y el total a pagar', () => {
    const element = render();

    expect(element.querySelector('[aria-labelledby="gifts-title"]')?.textContent).toContain(
      '2 unidades',
    );
    expect(element.querySelector('dd.total')?.textContent?.trim()).toBe('$73.542');
  });

  it('todas las imágenes pasan por el componente de imagen', () => {
    const images = [...render().querySelectorAll('img')];

    expect(images).toHaveLength(ORDER.lines.length + ORDER.gifts.length);
    expect(images.every((image) => image.parentElement?.tagName === 'APP-PRODUCT-IMAGE')).toBe(
      true,
    );
  });

  describe('con una orden en la URL', () => {
    const REAL = { ...ORDER, orderNumber: 'CO-A-00000001', status: 'CONFIRMED' };

    function renderOrder(lookup: OrderLookup) {
      TestBed.overrideProvider(ORDER_LOOKUP, { useValue: lookup });
      const fixture = TestBed.createComponent(OrderSummary);
      fixture.detectChanges();
      return fixture;
    }

    async function settle(fixture: ComponentFixture<OrderSummary>): Promise<HTMLElement> {
      await fixture.whenStable();
      fixture.detectChanges();
      return fixture.nativeElement;
    }

    const lookup = { orderId: 'o_1', tenantId: 'CO-MARCA-A', customerId: 'CLI-CONTADO' };

    it('muestra el aviso de carga mientras espera la respuesta', () => {
      const fixture = renderOrder(lookup);

      expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
        'Cargando la orden',
      );
      http().expectOne('/v1/orders/o_1');
    });

    it('pide la orden a la API con el tendero y la muestra', async () => {
      const fixture = renderOrder(lookup);
      const request = http().expectOne('/v1/orders/o_1');

      expect(request.request.headers.get('X-Tenant-Id')).toBe('CO-MARCA-A');
      expect(request.request.headers.get('X-Customer-Id')).toBe('CLI-CONTADO');
      request.flush(REAL);
      const element = await settle(fixture);

      expect(element.querySelector('h1')?.textContent?.trim()).toBe('Orden CO-A-00000001');
      expect(element.textContent).toContain('Confirmada');
      expect(element.querySelector('button')?.textContent?.trim()).toBe('Repetir pedido');
      expect(element.querySelector('dd.total')?.textContent?.trim()).toBe('$73.542');
    });

    it('codifica el id de la orden en la ruta', () => {
      renderOrder({ ...lookup, orderId: 'o/1 x' });

      http().expectOne('/v1/orders/o%2F1%20x');
    });

    it('oculta descuentos y obsequios vacíos y usa la imagen genérica para un SKU sin ilustración', async () => {
      const fixture = renderOrder(lookup);
      http()
        .expectOne('/v1/orders/o_1')
        .flush({
          ...REAL,
          lines: [{ ...ORDER.lines[0], sku: 'AGUA-500' }],
          discounts: [],
          gifts: [],
        });
      const element = await settle(fixture);

      expect(element.querySelector('#discounts-title')).toBeNull();
      expect(element.querySelector('#gifts-title')).toBeNull();
      expect(element.querySelector('img')?.getAttribute('src')).toBe('images/producto.svg');
    });

    it('cobra el IVA del obsequio cuando lo paga el cliente', async () => {
      const fixture = renderOrder(lookup);
      http()
        .expectOne('/v1/orders/o_1')
        .flush({ ...REAL, gifts: [{ ...ORDER.gifts[0], taxBorneBy: 'CUSTOMER' }] });
      const gifts = (await settle(fixture)).querySelector('#gifts-title + ul');

      expect(gifts?.textContent).not.toContain('Sin costo');
      expect(gifts?.textContent).toContain('$400');
      expect(gifts?.textContent).toContain('solo su IVA');
    });

    it('muestra el motivo cuando la API rechaza la consulta', async () => {
      const fixture = renderOrder(lookup);
      http()
        .expectOne('/v1/orders/o_1')
        .flush(
          { code: 'ORDER_NOT_FOUND', detail: 'La orden no existe.' },
          { status: 404, statusText: 'Not Found' },
        );

      const alert = (await settle(fixture)).querySelector('[role="alert"]');
      expect(alert?.textContent).toContain('La orden no existe.');
    });

    it('distingue una API caída de una respuesta sin motivo', async () => {
      const down = renderOrder(lookup);
      http().expectOne('/v1/orders/o_1').error(new ProgressEvent('error'));
      expect((await settle(down)).textContent).toContain('No se pudo conectar con la API.');

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [provideHttpClient(), provideHttpClientTesting()],
      });
      const rejected = renderOrder(lookup);
      http().expectOne('/v1/orders/o_1').flush('<html></html>', {
        status: 400,
        statusText: 'Bad Request',
      });
      expect((await settle(rejected)).textContent).toContain('La API rechazó la consulta (400).');
    });

    it('no pide nada si faltan el tenant o el tendero', () => {
      const fixture = renderOrder({ ...lookup, customerId: '' });

      expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
        'Faltan el tenant y el tendero',
      );
    });
  });
});
