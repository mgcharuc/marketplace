import { Component, input } from '@angular/core';

/** Nombre de producto completo. Si no cabe en una línea, crece hacia abajo y nunca se recorta. */
@Component({
  selector: 'app-product-name',
  host: { class: 'block min-w-0 wrap-anywhere hyphens-auto' },
  template: `{{ name() }}`,
})
export class ProductName {
  readonly name = input.required<string>();
}
