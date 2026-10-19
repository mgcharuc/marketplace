import { TestBed } from '@angular/core/testing';
import { ProductName } from './product-name';

describe('ProductName', () => {
  it('muestra un nombre de 120 caracteres completo', () => {
    const name = 'Gaseosa '.repeat(15);
    const fixture = TestBed.createComponent(ProductName);
    fixture.componentRef.setInput('name', name);
    fixture.detectChanges();

    expect(name.length).toBe(120);
    expect(fixture.nativeElement.textContent).toBe(name);
  });
});
