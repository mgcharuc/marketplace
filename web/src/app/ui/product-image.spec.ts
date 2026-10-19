import { TestBed } from '@angular/core/testing';
import { ProductImage } from './product-image';

describe('ProductImage', () => {
  it('muestra la imagen con su texto alternativo', () => {
    const fixture = TestBed.createComponent(ProductImage);
    fixture.componentRef.setInput('src', 'images/jugo-1l.svg');
    fixture.componentRef.setInput('alt', 'Jugo de naranja');
    fixture.detectChanges();

    const image: HTMLImageElement = fixture.nativeElement.querySelector('img');
    expect(image.getAttribute('src')).toBe('images/jugo-1l.svg');
    expect(image.alt).toBe('Jugo de naranja');
    expect(fixture.nativeElement.children).toHaveLength(1);
  });
});
