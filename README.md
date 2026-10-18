# Marketplace B2B: motor de pedidos

API para cotizar y confirmar pedidos de tenderos a distribuidores de bebidas, en varios países y marcas.

## Cómo correrlo

### Requisitos

- JDK 21. Con [SDKMAN](https://sdkman.io): `sdk env install` instala y activa la versión fijada en `.sdkmanrc`. La activación solo dura en esa terminal. En cada terminal nueva ejecute `sdk env` dentro del proyecto, o active `sdkman_auto_env=true` en `~/.sdkman/etc/config` para que se aplique al entrar a la carpeta. Con un JDK anterior, Maven falla con `has been compiled by a more recent version of the Java Runtime`.
- Docker o Podman para las pruebas de integración (PostgreSQL 17 con Testcontainers).

### Compilar y probar

```bash
./mvnw verify
```

Ejecuta las pruebas unitarias y de integración, el reporte de cobertura (`target/site/jacoco`) y la verificación de formato. Sin motor de contenedores, las pruebas de integración se omiten con un aviso en el log. Para exigirlas:

```bash
./mvnw verify -Dpostgres.required=true
```

### Usar Podman en lugar de Docker

Con Docker no se requiere configuración adicional. Con Podman rootless (Linux/WSL), active el socket compatible con la API de Docker e indique su ubicación a Testcontainers. Sin estos pasos, las pruebas de integración se omiten.

```bash
systemctl --user enable --now podman.socket
cat > ~/.testcontainers.properties <<EOF
docker.host=unix:///run/user/$(id -u)/podman/podman.sock
ryuk.container.privileged=true
EOF
```

### Ejecutar la aplicación en local

```bash
./mvnw spring-boot:test-run
```

Levanta la aplicación con un PostgreSQL temporal en un contenedor.

## Configuración por país y marca

Ningún valor de moneda, escala, redondeo ni tasa está escrito en el código. La configuración tiene tres niveles:

- **País**: moneda, escala, modo de redondeo, quién asume el impuesto del obsequio y las clases tributarias con su tasa. Lo comparten todas las marcas del país.
- **Tenant** (país y marca): prefijo del número de orden y tiempos de vigencia de la cotización y de la clave de idempotencia. Toma la configuración fiscal y monetaria de su país.
- **Versión comercial del tenant**: productos con su precio de lista y su clase tributaria, reglas de combo, escala y obsequio, y el orden de las etapas. Las versiones se numeran de forma consecutiva y una versión publicada no se modifica.

Antes de publicar una versión se valida completa: un pipeline con los impuestos después de las etapas comerciales, el crédito al final y sin etapas repetidas ni faltantes, tramos de escala crecientes, precio de combo por debajo del valor de lista, referencias a SKU, familias y clases tributarias que existan, precios en la escala de la moneda, reglas que compiten con la misma prioridad y vigencias coherentes. El error lista todas las violaciones juntas.

Las semillas están en `src/main/resources/seed/`. `tenants.json` define Colombia (COP, escala 0) y Ecuador (USD, escala 2), y tres tenants: `CO-MARCA-A`, `CO-MARCA-B` y `EC-MARCA-A`. Cada tenant tiene su versión inicial en `<tenant>.v1.json`. Un campo desconocido, uno obligatorio que falta o un país inexistente hacen fallar la carga con la ruta del campo.

Al arrancar, la aplicación crea el esquema con Flyway y guarda en PostgreSQL los tenants de las semillas que todavía no existen, con sus distribuidores, tenderos, cupos de crédito y versión inicial. Un tenant que ya está en la base no se vuelve a cargar, así que reiniciar no duplica datos ni deshace cambios. La configuración del país se lee de la base en cada uso, sin caché, así que un cambio de tasa se aplica desde la siguiente cotización en todos los tenants del país.

## Orden de aplicación de palancas

El motor (`PricingEngine`) ejecuta las etapas en el orden que define la versión comercial del tenant. Las semillas usan `COMBO → ESCALA → OBSEQUIO → IMPUESTOS → CREDITO`.

- **El combo va antes que la escala.** Es la regla más específica: un precio cerrado para un conjunto concreto de productos. Si la escala fuera primero, las unidades que después forman el combo ayudarían a alcanzar un tramo de escala y la misma unidad daría dos beneficios. Por ejemplo, con 5 GASEOSA-1500 y 3 SNACK-PAPAS, el combo consume 2 gaseosas y la escala cuenta solo las 3 que quedan: descuento total 1650. Con la escala primero serían 2150.
- **Una unidad recibe como máximo un descuento.** La escala cuenta posiciones solo sobre las unidades que el combo no consumió.
- **Dentro de una etapa**, las reglas se aplican por prioridad y luego por id, y cada una toma todo lo que puede de las unidades que quedan. No se busca la combinación más favorable para el cliente.
- **Los impuestos van después de los descuentos**, porque la base gravable es lo que queda después de los descuentos comerciales. El descuento del combo se reparte entre sus líneas solo para calcular la base gravable de cada producto, que puede tener otra tasa. No aparece como un descuento por producto.
- **El obsequio va después de los descuentos.** Cuenta las unidades pagadas de su alcance, con o sin descuento. Con una condición por monto usa la base gravable que queda después de los descuentos. Las unidades obsequiadas no son líneas del pedido, así que no activan escalas, combos ni otros obsequios. Cada regla aparece por separado en `gifts`, con precio de referencia y monto cobrado 0. Su impuesto se calcula sobre el precio de lista y depende del país. Puede no tener impuesto, quedar a cargo del vendedor, que lo informa sin sumarlo al total, o a cargo del cliente. En este último caso su base entra en la base gravable y su impuesto en el total. El obsequio en sí nunca se cobra.
- **El crédito va al final**, porque compara el total con impuestos con el cupo disponible del tendero con ese distribuidor. Informa si el pedido cabe y cuánto falta, sin reservar cupo. En una compra de contado `credit` es `null`.
- Las etapas comerciales (combo, escala y obsequio) se pueden reordenar u omitir en una versión, y una etapa omitida no se aplica. Los impuestos siempre van después de ellas y el crédito al final. Una versión que no lo cumple no se publica. Si hay un obsequio por monto, además debe ir después de los descuentos.

## Política de redondeo

Los montos son `BigDecimal` envueltos en `Money`, que lleva su moneda. Una prueba de arquitectura (`ArchitectureTest`) hace fallar el build si aparece un `double` o un `float`, solo o dentro de un arreglo o una lista, en el dominio, en el motor de precios o en el código que lee y escribe JSON, si el dominio o el motor convierten un monto a `double` o si un `BigDecimal` se crea desde un `double`. La misma prueba exige que el dominio y el motor no dependan de ningún framework.

- Cada moneda define su escala y su modo de redondeo, `HALF_UP` o `HALF_EVEN`. La escala es la de la práctica comercial, que puede no coincidir con la oficial.
- `Money` completa con ceros un valor con menos decimales que la moneda (`2.3` pasa a `2.30` en USD) y rechaza uno con más. Nunca redondea por su cuenta.
- Los cálculos intermedios, como multiplicar por una tasa, devuelven `BigDecimal` con precisión completa. `Money.round` es el único paso de un valor intermedio a un monto.
- Solo se redondea en tres puntos: el descuento de cada escala sobre cada línea, el descuento total de cada combo antes de repartirlo y el impuesto de cada línea y de cada obsequio. Los totales del pedido son sumas de valores ya redondeados y nunca se redondean, así el total coincide con la suma de las líneas, igual que en la factura y en el ERP.
- Para repartir un monto en partes proporcionales se usa el método del mayor residuo (`Allocation.split`). Cada cuota se trunca a la escala de la moneda y las unidades mínimas que sobran van, de a una, a las partes con mayor fracción descartada. Si dos partes empatan, gana el SKU menor o el tramo que empieza antes. La suma de las cuotas siempre es el monto repartido.

## Casos golden

Los casos de `src/test/resources/golden/` comprueban el motor de punta a punta: cada carpeta tiene el carrito y el desglose esperado, calculado a mano. `GoldenTest` los ejecuta todos y además revisa que los totales cuadren con la suma de líneas, descuentos, tramos y obsequios, y que el crédito compare el total con el cupo. La derivación de cada monto está en `src/test/resources/golden/README.md`.

## Pendientes y por qué

- **Vigencia de las reglas.** La versión acepta `validFrom` y `validTo` y valida que el inicio sea anterior al fin, pero el motor aplica todas las reglas sin mirar la fecha. Se priorizaron las palancas que pide el enunciado y ninguna regla de las semillas tiene vigencia.
