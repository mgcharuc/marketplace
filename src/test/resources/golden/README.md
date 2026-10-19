# Casos golden del motor

Cada carpeta tiene un `case.json` con la semilla, los cambios sobre ella (`overrides`) y el carrito, y un `expected.json` con el desglose esperado. `GoldenTest` ejecuta todas las carpetas, revisa que los totales cuadren y compara `configVersion`, `currency`, `lines`, `discounts`, `gifts`, `totals` y `credit`.

Los valores esperados se calcularon a mano a partir de las reglas de negocio, nunca copiando la salida del código. Abajo está la derivación de cada caso.

Datos de la semilla que usan los casos:

| SKU | Precio de lista | Tasa |
|---|---|---|
| GASEOSA-350 | 2000 COP | 19 % |
| GASEOSA-1500 | 5000 COP | 19 % |
| JUGO-1L | 4000 COP | 5 % |
| SNACK-PAPAS | 1800 COP | 19 % |
| AGUA-500 | 0.35 USD | 15 % |
| JUGO-EC-1L | 1.25 USD | 0 % |

Colombia asume el impuesto del obsequio en el vendedor: se calcula e informa, pero no entra en el total. Ecuador no grava los obsequios.

Reglas: Combo Fiesta (2 GASEOSA-1500 + 3 SNACK-PAPAS por 14000), Combo Desayuno (2 JUGO-1L + 2 SNACK-PAPAS por 9000), Escala Gaseosa 350 (marginal, 5 % desde la unidad 10 y 10 % desde la 20), Escala Gaseosa 1,5 L (marginal, 5 % desde la unidad 3), Jugo por cada 12 gaseosas (un JUGO-1L por cada 12 GASEOSA-350 pagadas, repetible), Combo Hidratación (3 AGUA-500 + 1 JUGO-EC-1L por 2.00 USD) y Escala Agua (todas las unidades, 3 % desde 5).

## `combo-sin-descuento-suelto` (golden obligatorio 2)

Carrito: GASEOSA-1500 × 3 y SNACK-PAPAS × 3.

- Combo Fiesta se aplica una vez. Valor de lista del conjunto 2 × 5000 + 3 × 1800 = 15400, descuento 15400 − 14000 = 1400.
- Reparto del descuento en proporción al valor consumido (10000 y 5400): 909,09 y 490,91. Se truncan a 909 y 490 y la unidad que sobra va a SNACK-PAPAS, que tiene la mayor fracción: 909 y 491.
- Queda una sola GASEOSA-1500 elegible, así que la escala desde 3 no aplica.
- GASEOSA-1500: 15000 − 909 = 14091, IVA 2677,29 → 2677. SNACK-PAPAS: 5400 − 491 = 4909, IVA 932,71 → 933.
- Total 19000 + 3610 = 22610. El combo es el único descuento y las líneas no muestran descuentos propios.

## `combo-con-impuestos` (golden obligatorio 4)

Carrito: JUGO-1L × 2 y SNACK-PAPAS × 2.

- Combo Desayuno una vez: lista 2 × 4000 + 2 × 1800 = 11600, descuento 2600.
- Reparto sobre 8000 y 3600: 1793,10 y 806,90 → 1793 y 807.
- JUGO-1L: 8000 − 1793 = 6207, IVA 5 % = 310,35 → 310. SNACK-PAPAS: 3600 − 807 = 2793, IVA 19 % = 530,67 → 531.
- Total 9000 + 841 = 9841.

## `borde-del-tramo-sin-descuento`

Carrito: GASEOSA-350 × 9. No llega a la unidad 10: sin descuento. Base 18000, IVA 3420, total 21420.

## `borde-del-tramo-con-descuento`

Carrito: GASEOSA-350 × 10. Solo la unidad 10 cae en el tramo del 5 %: 2000 × 5 % = 100. Base 19900, IVA 3781, total 23681.

## `combo-dos-veces-con-sobrante`

Carrito: GASEOSA-1500 × 5 y SNACK-PAPAS × 6.

- Combo Fiesta se aplica dos veces (5 / 2 = 2 y 6 / 3 = 2): descuento 2 × 1400 = 2800.
- Reparto sobre 20000 y 10800: 1818,18 y 981,82 → 1818 y 982.
- Sobra una GASEOSA-1500 elegible, que no alcanza la escala desde 3.
- GASEOSA-1500: 25000 − 1818 = 23182, IVA 4404,58 → 4405. SNACK-PAPAS: 10800 − 982 = 9818, IVA 1865,42 → 1865.
- Total 33000 + 6270 = 39270.

## `dolares-escala-todas-las-unidades`

Tenant de Ecuador. Carrito: AGUA-500 × 7.

- Bruto 7 × 0.35 = 2.45. Con 7 unidades se alcanza el tramo desde 5 y el 3 % aplica a todas: 0.0735 → 0.07.
- Base 2.38, IVA 15 % = 0.357 → 0.36. Total 2.74.

## `escala-por-familia`

Cambio sobre la semilla: las dos escalas se reemplazan por una escala de la familia GASEOSAS, marginal, con 5 % desde la unidad 10.

Carrito: GASEOSA-350 × 6 y GASEOSA-1500 × 6.

- Las unidades se ordenan de la más cara a la más barata: posiciones 1 a 6 para GASEOSA-1500 y 7 a 12 para GASEOSA-350.
- Las posiciones 10 a 12 son tres GASEOSA-350: 3 × 2000 × 5 % = 300, todo en esa línea.
- GASEOSA-350: 12000 − 300 = 11700, IVA 2223. GASEOSA-1500: 30000, IVA 5700.
- Total 41700 + 7923 = 49623.

## `combo-en-dolares-con-residuo`

Tenant de Ecuador. Carrito: AGUA-500 × 3 y JUGO-EC-1L × 1.

- Combo Hidratación una vez: lista 3 × 0.35 + 1.25 = 2.30, descuento 0.30.
- Reparto sobre 1.05 y 1.25: 0.1369 y 0.1630. Se truncan a 0.13 y 0.16 y el centavo que sobra va a AGUA-500, que tiene la mayor fracción: 0.14 y 0.16.
- La escala de agua no aplica porque el combo consumió las tres unidades.
- AGUA-500: 1.05 − 0.14 = 0.91, IVA 0.1365 → 0.14. JUGO-EC-1L: 1.25 − 0.16 = 1.09, IVA 0.
- Total 2.00 + 0.14 = 2.14.

## `escala-cuenta-solo-elegibles`

Carrito: GASEOSA-1500 × 5 y SNACK-PAPAS × 3.

- Combo Fiesta una vez (lo limita SNACK-PAPAS): consume 2 GASEOSA-1500, descuento 1400 repartido 909 y 491.
- Quedan 3 GASEOSA-1500 elegibles en las posiciones 1 a 3. Solo la posición 3 cae en el tramo: 5000 × 5 % = 250. Si se contaran las 5 unidades, el descuento sería 750.
- GASEOSA-1500: 25000 − 250 − 909 = 23841, IVA 4529,79 → 4530. SNACK-PAPAS: 4909, IVA 933.
- Total 28750 + 5463 = 34213.

## `escala-dos-tramos` (golden obligatorio 1)

Carrito: GASEOSA-350 × 25.

- Escala marginal: las unidades 10 a 19 van al 5 %, 10 × 2000 × 5 % = 1000, y las 20 a 25 al 10 %, 6 × 2000 × 10 % = 1200. Descuento 2200.
- Base 50000 − 2200 = 47800, IVA 9082. Total 56882.
- Obsequio: 12 cabe 2 veces completas en 25, así que son 2 jugos. Base 2 × 4000 = 8000, IVA 5 % = 400 a cargo del vendedor, fuera del total.

## `obsequio-con-su-cantidad` (golden obligatorio 3)

Carrito: GASEOSA-350 × 12.

- Escala: las unidades 10 a 12 al 5 %, 3 × 2000 × 5 % = 300. Base 23700, IVA 4503. Total 28203.
- El obsequio cuenta las 12 unidades pagadas, también las tres con descuento: 1 JUGO-1L con precio de referencia 4000, cobrado 0, base 4000 e IVA 200 a cargo del vendedor.

## `credito-excede-el-cupo` (golden obligatorio 5)

Tendero CLI-CREDITO-BAJO con DIST-NORTE, cupo 50000, a crédito. Carrito y desglose de `escala-dos-tramos`.

- Requerido = total = 56882. No cabe: 56882 > 50000. Falta 56882 − 50000 = 6882.

## `obsequio-impuesto-a-cargo-del-vendedor`, `obsequio-impuesto-a-cargo-del-cliente` y `obsequio-sin-impuesto`

Carrito de `obsequio-con-su-cantidad`, cambiando la política del obsequio del país. La línea no cambia: base 23700, IVA 4503.

- A cargo del vendedor (la de Colombia): tasa 0.05, impuesto 200 informado con el vendedor como responsable. Base gravable 23700, impuestos 4503, total 28203.
- A cargo del cliente: el obsequio suma su base y su impuesto. Base gravable 23700 + 4000 = 27700, impuestos 4503 + 200 = 4703, total 23700 + 4703 = 28403.
- Sin impuesto: tasa y responsable en `null`, impuesto 0. Totales iguales a los del vendedor: 28203.

## `obsequio-repetible-y-escala-completa`

Carrito: GASEOSA-350 × 24.

- Escala: unidades 10 a 19 al 5 %, 10 × 100 = 1000, y 20 a 24 al 10 %, 5 × 200 = 1000. Descuento 2000.
- Base 46000, IVA 8740. Total 54740.
- Obsequio: 12 cabe 2 veces en 24, así que son 2 jugos, base 8000, IVA 400 a cargo del vendedor.

## `obsequio-no-activa-palancas`

Cambios sobre la semilla: el obsequio regala una GASEOSA-350 por cada 12 y la escala de GASEOSA-350 pasa a 5 % desde la unidad 13.

Carrito: GASEOSA-350 × 12.

- Obsequio: 1 GASEOSA-350, base 2000, IVA 19 % = 380 a cargo del vendedor.
- Con la unidad obsequiada serían 13 y se activaría la escala, pero solo cuentan las 12 pagadas: sin descuento.
- Base 24000, IVA 4560. Total 28560.

## `vigencia-escala-antes-del-fin` y `vigencia-escala-vencida-en-el-borde`

Cambios sobre la semilla: la escala de GASEOSA-350 vale hasta `2026-10-17T00:00:00-05:00`. Carrito de `escala-dos-tramos`.

- A las 23:55 (-05:00), cinco minutos antes del fin, la escala aplica. Las unidades 10 a 19 van al 5 %, 10 × 2000 × 5 % = 1000, y las 20 a 25 al 10 %, 6 × 2000 × 10 % = 1200. Base 47800, IVA 9082. Total 56882.
- A las 00:00 (-05:00) la escala ya no aplica, porque el fin no se incluye. Base 50000, IVA 9500. Total 59500.
- El obsequio no tiene fechas de vigencia y en los dos casos da 2 jugos, base 8000 e IVA 400 a cargo del vendedor.

## `escala-antes-que-combo`

Cambio sobre la semilla: el pipeline pone la escala antes que el combo. Carrito de `combo-sin-descuento-suelto`.

- La escala de GASEOSA-1500 cuenta las 3 unidades, incluidas las 2 que después forman el combo, así que alcanza el tramo desde la unidad 3. Esa unidad lleva el 5 %, 5000 × 5 % = 250.
- Combo Fiesta usa las 2 GASEOSA-1500 que quedan y las 3 SNACK-PAPAS. Descuento 1400, repartido 909 y 491.
- GASEOSA-1500: 15000 − 250 − 909 = 13841, IVA 2629,79 → 2630. SNACK-PAPAS: 4909, IVA 933.
- Descuentos 1650, base 18750, impuestos 3563. Total 22313. Con el combo primero la escala solo ve una gaseosa, no llega al tramo y el total es 22610.

## `combos-que-compiten`

Carrito: GASEOSA-1500 × 2, JUGO-1L × 2 y SNACK-PAPAS × 3.

- Combo Fiesta, con prioridad 10, se aplica primero y usa las 3 SNACK-PAPAS. Descuento 1400, repartido 909 y 491.
- Combo Desayuno, con prioridad 20, necesita 2 SNACK-PAPAS y ya no quedan, así que no aparece en el desglose.
- GASEOSA-1500: 10000 − 909 = 9091, IVA 1727,29 → 1727. JUGO-1L sin descuento: 8000, IVA 5 % = 400. SNACK-PAPAS: 4909, IVA 933.
- Bruto 23400, descuentos 1400, base 22000, impuestos 3060. Total 25060.
