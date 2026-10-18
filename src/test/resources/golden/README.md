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

Reglas: Combo Fiesta (2 GASEOSA-1500 + 3 SNACK-PAPAS por 14000), Combo Desayuno (2 JUGO-1L + 2 SNACK-PAPAS por 9000), Escala Gaseosa 350 (marginal, 5 % desde la unidad 10 y 10 % desde la 20), Escala Gaseosa 1,5 L (marginal, 5 % desde la unidad 3), Combo Hidratación (3 AGUA-500 + 1 JUGO-EC-1L por 2.00 USD) y Escala Agua (todas las unidades, 3 % desde 5).

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
