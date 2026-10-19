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

Levanta la aplicación con un PostgreSQL temporal en un contenedor, en el puerto 8080.

## API

Las cotizaciones y las órdenes identifican al tendero con las cabeceras `X-Tenant-Id` y `X-Customer-Id`. `X-Correlation-Id` es opcional. Si no llega o no es válido se genera uno, y siempre vuelve en la respuesta.

Las semillas traen tenderos para probar los dos métodos de pago. En `CO-MARCA-A`, `CLI-CONTADO` no tiene cupo, `CLI-CREDITO-ALTO` tiene cupo con `DIST-NORTE` y `CLI-CREDITO-BAJO` con `DIST-NORTE` y `DIST-SUR`. En `EC-MARCA-A`, `CLI-EC-01` tiene cupo con `DIST-EC-01`.

Cotizar un carrito:

```bash
curl -i http://localhost:8080/v1/quotes \
  -H 'X-Tenant-Id: CO-MARCA-A' -H 'X-Customer-Id: CLI-CONTADO' -H 'Content-Type: application/json' \
  -d '{"distributorId": "DIST-NORTE", "paymentMethod": "CASH",
       "items": [{"sku": "GASEOSA-1500", "quantity": 3}, {"sku": "SNACK-PAPAS", "quantity": 3}]}'
```

Responde 201 con el desglose completo y `Location: /v1/quotes/{quoteId}`. La cotización vence según la vigencia del tenant, 15 minutos en las semillas. Consultarla devuelve el mismo cuerpo, byte a byte, porque se guarda el JSON tal como se respondió y no se vuelve a calcular. Solo la ve el tendero que la pidió.

```bash
curl -i http://localhost:8080/v1/quotes/{quoteId} \
  -H 'X-Tenant-Id: CO-MARCA-A' -H 'X-Customer-Id: CLI-CONTADO'
```

Los SKU repetidos se suman en una sola línea. A crédito, `credit` informa el cupo disponible del tendero con ese distribuidor, el total requerido y cuánto falta, sin reservar nada.

Confirmar una cotización vigente:

```bash
curl -i http://localhost:8080/v1/orders \
  -H 'X-Tenant-Id: CO-MARCA-A' -H 'X-Customer-Id: CLI-CONTADO' -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: pedido-001' \
  -d '{"quoteId": "{quoteId}"}'
```

Responde 201 con la orden, su número (`CO-A-00000001`) y `Location: /v1/orders/{orderId}`. La orden copia el desglose guardado en la cotización sin recalcularlo, así que el total es el cotizado aunque después cambien los precios, las reglas o las tasas. A crédito, `credit` informa el cupo consumido y el que queda con ese distribuidor. Consultar la orden devuelve el mismo cuerpo del 201, byte a byte, y solo la ve el tendero que la confirmó.

La cabecera `Idempotency-Key` es obligatoria, con 1 a 128 caracteres ASCII visibles. Un reintento con la misma clave y el mismo cuerpo recibe el 201 original, byte a byte, con `Idempotent-Replayed: true`, y no crea otra orden ni consume cupo otra vez. La misma clave con otro cuerpo responde 422. Cada tendero tiene sus propias claves, que vencen según el tenant, 24 horas en las semillas.

```bash
curl -i http://localhost:8080/v1/orders/{orderId} \
  -H 'X-Tenant-Id: CO-MARCA-A' -H 'X-Customer-Id: CLI-CONTADO'
```

El historial del tendero va de la orden más reciente a la más antigua, con `page` desde 0 y `size` de 1 a 100 (20 por defecto). Se lee de la misma base donde se confirma, así que una orden aparece apenas se responde su 201.

```bash
curl -i 'http://localhost:8080/v1/orders?page=0&size=20' \
  -H 'X-Tenant-Id: CO-MARCA-A' -H 'X-Customer-Id: CLI-CONTADO'
```

Los errores usan `application/problem+json` con un `code` estable y el `correlationId`:

- 400 cuando la forma de la petición no es válida: faltan las cabeceras de identidad o la `Idempotency-Key`, el JSON está mal formado, hay campos desconocidos, un tipo es incorrecto (`"quantity": "3"` no se convierte), falta un campo obligatorio o la paginación está fuera de rango.
- 404 si el tenant, el tendero, la cotización, la orden, el evento o la ruta no existen.
- 405 si la ruta no admite el método y 415 si el cuerpo no es `application/json`.
- 409 si la cotización venció (con `expiresAt`) o ya se confirmó (con su `orderId`), o si el evento que se reencola no está fallido (con `eventStatus`).
- 422 cuando el contenido no se puede cotizar porque el carrito tiene errores, el distribuidor no es del tenant o no hay cuenta de crédito con ese distribuidor. También al confirmar a crédito si el cupo ya no alcanza (con `available` y `required`) o si la cuenta dejó de existir, y cuando la `Idempotency-Key` ya se usó con otro cuerpo.

Los errores de forma y los del carrito traen `errors[]` con la posición del ítem, el campo, el SKU recibido y el código. El carrito informa todos sus errores juntos.

## Configuración por país y marca

Ningún valor de moneda, escala, redondeo ni tasa está escrito en el código. La configuración tiene tres niveles:

- **País**: moneda, escala, modo de redondeo, quién asume el impuesto del obsequio y las clases tributarias con su tasa. Lo comparten todas las marcas del país.
- **Tenant** (país y marca): prefijo del número de orden y tiempos de vigencia de la cotización y de la clave de idempotencia. Toma la configuración fiscal y monetaria de su país.
- **Versión comercial del tenant**: productos con su precio de lista y su clase tributaria, reglas de combo, escala y obsequio, y el orden de las etapas. Las versiones se numeran de forma consecutiva y una versión publicada no se modifica.

Cada regla puede tener una vigencia con `validFrom` y `validTo`, en ISO-8601 con cualquier offset. `validFrom` se incluye y `validTo` no, y si falta uno de los dos ese lado queda abierto. La vigencia se revisa solo al cotizar, así que la orden mantiene la promoción cotizada aunque venza antes de confirmar.

Antes de publicar una versión se valida completa: un pipeline con los impuestos después de las etapas comerciales, el crédito al final y sin etapas repetidas ni faltantes, tramos de escala crecientes, precio de combo por debajo del valor de lista, referencias a SKU, familias y clases tributarias que existan, precios de lista mayores que cero y en la escala de la moneda, reglas que compiten con la misma prioridad y vigencias coherentes. El error lista todas las violaciones juntas.

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

## Confirmación

Confirmar es una sola transacción en PostgreSQL. Si algo falla antes del commit no queda orden, consumo de cupo, eventos ni la clave reservada, y la respuesta es un error. El 201 sale después del commit, así que una orden respondida ya se puede consultar.

- **Reservar la clave de idempotencia es lo primero de la transacción.** Un reintento simultáneo con la misma clave espera a que termine la primera petición y recibe su respuesta. Solo se guardan los 201, así que un rechazo se evalúa de nuevo al reintentar. El cuerpo se compara por el SHA-256 de su JSON ya normalizado, así que los espacios no cuentan. Con la clave vencida, reintentar la misma cotización responde 409 con su orden.
- **Después se bloquea la cotización.** Dos confirmaciones de la misma cotización se ordenan en ese bloqueo, y la segunda encuentra la orden de la primera y responde 409 con su `orderId`. La restricción única sobre `quote_id` es una segunda barrera y da la misma respuesta.
- **Una cotización ya confirmada responde con su orden antes de mirar el vencimiento.** Quien reintenta tarde recibe la orden que ya tiene y no un aviso de cotización vencida.
- **La cotización vence en `expiresAt` exacto.** Se puede confirmar hasta el segundo anterior. El tiempo de vigencia acota cuánto se respeta un precio anterior.
- **El cupo se descuenta con un `UPDATE` condicional** (`available >= total`) en lugar de leer la cuenta, restar y guardarla. Dos órdenes que caben por separado pero no juntas, confirmadas a la vez, nunca dejan el cupo negativo. Que la cotización dijera que cabía no garantiza la confirmación, porque el cupo pudo consumirse mientras tanto. Cada consumo queda como un movimiento ligado a la orden.
- **El número de orden sale de una secuencia de PostgreSQL** que no bloquea. Como se toma después de las validaciones, un rechazo no gasta número. Solo un fallo posterior, antes del commit, deja un hueco, y eso está permitido porque no es una numeración fiscal.
- **Los avisos al ERP y al tendero se guardan en la misma transacción** que la orden, en una tabla de eventos pendientes. Así no quedan avisos de órdenes que no existen ni órdenes sin aviso, y ningún sistema externo se llama antes del commit.

## Entrega al ERP y al tendero

Un proceso de la aplicación (`OutboxRelay`) revisa cada segundo los eventos pendientes y entrega cada uno en su propia transacción, con su fila bloqueada. Si se levantan varias instancias, cada una salta los eventos que otra tiene tomados.

- **La entrega es al menos una vez.** Si el proceso cae después de entregar y antes de marcar el evento como enviado, el evento vuelve a salir con el mismo `eventId`. El ERP lo usa como clave de idempotencia y descarta el segundo envío.
- **Una notificación entregada no se vuelve a enviar.** Cada entrega se guarda en su propia transacción, que queda confirmada aunque la del evento se revierta, y el relay la revisa antes de enviar. La notificación lleva además el `orderId` como clave de agrupación, así que el dispositivo junta las repetidas.
- **Si el destino falla, el evento se reintenta** con una espera que empieza en un segundo, se duplica con cada fallo hasta 15 minutos y suma hasta un 20 % al azar, para que muchos eventos no reintenten a la vez. Cualquier error del entregador cuenta como un intento.
- **Cada destino tiene su ventana.** El ERP se reintenta durante 72 horas y después el evento queda fallido. La notificación solo sirve si llega pronto, así que pasada una hora se descarta, aunque el proveedor esté disponible.
- **El ERP va antes que la notificación** cuando los dos están pendientes, porque los ids de evento son crecientes y el relay desempata por id.

Un evento fallido se vuelve a encolar con su `eventId`, que aparece en el log del fallo. Queda pendiente, sin intentos y con 72 horas nuevas desde ese momento. Es una operación de soporte, así que no lleva cabeceras de identidad ni filtra por tenant. Responde 202, 404 si el evento no existe y 409 si no está fallido, con su estado en `eventStatus`. Una notificación descartada no se reencola, porque ya llegaría tarde.

```bash
curl -i -X POST http://localhost:8080/v1/admin/outbox/{eventId}/retry
```

## Modelo de consistencia

- **La confirmación es de consistencia fuerte.** La orden, el consumo de cupo, la clave de idempotencia y los avisos pendientes se escriben en una sola transacción de PostgreSQL. Si la base no está disponible no se confirman pedidos, porque se prefiere la consistencia a la disponibilidad.
- **Los avisos al ERP y al tendero son de consistencia eventual.** Llegan después del 201, entregados al menos una vez, y cada destino descarta o agrupa los repetidos. No existe una entrega exactamente una vez de punta a punta hacia sistemas externos.
- **Lo que se paga por esto.** La cuenta de crédito del par queda bloqueada mientras se confirma. El 201 garantiza que la orden quedó registrada, no que el ERP la aceptó. Un evento del ERP que no se entrega en 72 horas necesita que alguien lo revise, y una notificación que no llega en una hora se pierde.

## Política de redondeo

Los montos son `BigDecimal` envueltos en `Money`, que lleva su moneda. Una prueba de arquitectura (`ArchitectureTest`) hace fallar el build si aparece un `double` o un `float`, solo o dentro de un arreglo o una lista, en el dominio, en el motor de precios o en el código que lee y escribe JSON, si el dominio o el motor convierten un monto a `double` o si un `BigDecimal` se crea desde un `double`.

- Cada moneda define su escala y su modo de redondeo, `HALF_UP` o `HALF_EVEN`. La escala es la de la práctica comercial, que puede no coincidir con la oficial.
- `Money` completa con ceros un valor con menos decimales que la moneda (`2.3` pasa a `2.30` en USD) y rechaza uno con más. Nunca redondea por su cuenta.
- Los cálculos intermedios, como multiplicar por una tasa, devuelven `BigDecimal` con precisión completa. `Money.round` es el único paso de un valor intermedio a un monto.
- Solo se redondea en tres puntos: el descuento de cada escala sobre cada línea, el descuento total de cada combo antes de repartirlo y el impuesto de cada línea y de cada obsequio. Los totales del pedido son sumas de valores ya redondeados y nunca se redondean, así el total coincide con la suma de las líneas, igual que en la factura y en el ERP.
- Para repartir un monto en partes proporcionales se usa el método del mayor residuo (`Allocation.split`). Cada cuota se trunca a la escala de la moneda y las unidades mínimas que sobran van, de a una, a las partes con mayor fracción descartada. Si dos partes empatan, gana el SKU menor o el tramo que empieza antes. La suma de las cuotas siempre es el monto repartido.

## Casos golden

Los casos de `src/test/resources/golden/` comprueban el motor de punta a punta: cada carpeta tiene el carrito y el desglose esperado, calculado a mano. `GoldenTest` los ejecuta todos y además revisa que los totales cuadren con la suma de líneas, descuentos, tramos y obsequios, y que el crédito compare el total con el cupo. La derivación de cada monto está en `src/test/resources/golden/README.md`.

## Organización del código

Es un monolito con puertos y adaptadores. Las dependencias van siempre hacia adentro, y `ArchitectureTest` hace fallar el build si alguna se invierte.

- `domain` tiene los conceptos del negocio (dinero, productos, configuración, cotización, orden, cupo y eventos) y `pricing` el motor de precios. Son Java puro. El dominio no depende de nadie y el motor solo del dominio.
- `application` tiene los casos de uso y los puertos que usan. No conoce Spring, JPA ni los adaptadores.
- `adapters` tiene la API web y las implementaciones de los puertos (JSON, PostgreSQL, entrega al ERP y al tendero, reloj, ids y jitter). Un adaptador no usa a otro.
- `bootstrap` registra como beans los casos de uso, el relay, su planificador y la carga de semillas. Ninguna otra capa depende de él.

## Pendientes y por qué

- **Dos casos golden del motor.** La escala antes que el combo y dos combos que compiten por un SKU ya tienen pruebas unitarias en `PricingEngineTest`, pero no un caso golden. Se priorizaron los golden que pide el enunciado.
- **Umbrales estrictos de cobertura.** El build exige 80 % de líneas en el dominio y en el motor. Las pruebas unitarias cubren hoy el 89 % de las líneas del dominio y el 98 % de las del motor, y más del 93 % de las ramas en los dos. El umbral no se subió al 90 % de líneas porque habría que escribir pruebas solo para alcanzar el número.

