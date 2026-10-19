# Resumen del pedido

Pantalla en Angular 22 y Tailwind 4 con el resumen de una orden: líneas, descuentos con su origen, obsequios, impuestos y total. Muestra una orden de la API o, si no recibe una, un pedido de ejemplo (`src/app/order-summary/order-fixture.ts`).

## Cómo correrlo

Requiere Node 24, fijado en `.nvmrc`.

```bash
npm ci
npm start        # http://localhost:4200
npm test
npm run lint
```

### Ver una orden de la API

`npm start` reenvía `/v1` a `http://localhost:8080`. Con el backend corriendo (`./mvnw spring-boot:test-run` en la raíz del repo), se confirma una orden con los `curl` del README principal y se abre con el tendero que la confirmó.

```
http://localhost:4200/?orderId={orderId}&tenant=CO-MARCA-A&customer=CLI-CONTADO
```

## Componentes

Tres componentes de `src/app/ui` resuelven lo que ninguna pantalla debe repetir por su cuenta.

- **`ProductName`** muestra el nombre completo. Si no cabe en una línea, crece en altura en lugar de recortarse.
- **`ProductImage`** pone la imagen en un marco cuadrado con `object-fit: contain`, así que todas las celdas de un grid miden lo mismo sin deformar la imagen.
- **`SafeAreaLayout`** deja la barra de acciones fija abajo, con el margen de la barra del sistema, y ajusta su alto al área visible cuando se abre el teclado (`interactive-widget` en Android y `visualViewport` en iOS).

`npm run lint` rechaza los `<img>` directos y cualquier recorte de texto (`text-overflow`, `line-clamp`, `truncate`), así que estos componentes no se pueden esquivar.

Los colores, la tipografía y los radios son tokens definidos en `src/styles.css`. La paleta por defecto de Tailwind está desactivada.

## Pruebas

`npm test` cubre los componentes, el formato de montos y tasas, y la carga de la orden desde la API con sus errores. El layout se revisó en Edge a 360 y 768 px de ancho y con el teclado abierto.
