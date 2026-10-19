import { Component, input } from '@angular/core';

/**
 * Imagen de producto dentro de un marco cuadrado. La imagen se ajusta al marco sin deformarse,
 * así que todas las celdas de un grid miden lo mismo aunque las imágenes tengan otra proporción.
 * El `alt` va vacío cuando el nombre ya se muestra al lado.
 */
@Component({
  selector: 'app-product-image',
  host: {
    class: 'block aspect-square w-full overflow-hidden rounded-md border border-line bg-white',
  },
  template: `<img
    class="block size-full object-contain p-1"
    [src]="src()"
    [alt]="alt()"
    loading="lazy"
    decoding="async"
  />`,
})
export class ProductImage {
  readonly src = input.required<string>();
  readonly alt = input('');
}
