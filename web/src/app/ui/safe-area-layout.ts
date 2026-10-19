import { Component, DestroyRef, ElementRef, inject } from '@angular/core';

/**
 * Pantalla con contenido desplazable y una barra de acciones fija abajo, que suma a sus márgenes
 * los de la barra del sistema. Ocupa exactamente el área visible, que se achica cuando se abre el
 * teclado, así que el teclado no tapa la barra.
 */
@Component({
  selector: 'app-safe-area-layout',
  host: {
    class:
      'fixed inset-x-0 top-[var(--visible-top,0px)] flex h-[var(--visible-height,100dvh)] flex-col pt-[var(--safe-top)] pr-[var(--safe-right)] pl-[var(--safe-left)]',
  },
  template: `
    <main class="min-h-0 flex-1 overflow-y-auto"><ng-content /></main>
    <footer
      class="shrink-0 border-t border-line bg-white px-4 pt-3 pb-[calc(0.75rem+var(--safe-bottom))]"
    >
      <ng-content select="[actions]" />
    </footer>
  `,
})
export class SafeAreaLayout {
  constructor() {
    // Safari de iOS no achica 100dvh con el teclado y además desplaza el área visible, así que se
    // siguen su alto y su posición.
    const viewport = globalThis.visualViewport;
    if (!viewport) {
      return;
    }
    const host: HTMLElement = inject(ElementRef).nativeElement;
    const follow = () => {
      host.style.setProperty('--visible-height', `${viewport.height}px`);
      host.style.setProperty('--visible-top', `${viewport.offsetTop}px`);
    };
    viewport.addEventListener('resize', follow);
    viewport.addEventListener('scroll', follow);
    inject(DestroyRef).onDestroy(() => {
      viewport.removeEventListener('resize', follow);
      viewport.removeEventListener('scroll', follow);
    });
  }
}
