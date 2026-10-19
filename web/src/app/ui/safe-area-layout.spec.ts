import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SafeAreaLayout } from './safe-area-layout';

@Component({
  imports: [SafeAreaLayout],
  template: `
    <app-safe-area-layout>
      <p>Contenido</p>
      <button actions>Confirmar</button>
    </app-safe-area-layout>
  `,
})
class Host {}

describe('SafeAreaLayout', () => {
  it('deja la acción en la barra de abajo y el resto en el contenido', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('footer button')?.textContent).toBe('Confirmar');
    expect(element.querySelector('main p')?.textContent).toBe('Contenido');
    expect(element.querySelector('main button')).toBeNull();
  });
});
