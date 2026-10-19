# ADR 001. Cálculo determinista y confirmación atómica de pedidos

**Contexto.** El tendero debe pagar lo cotizado aunque cambien precios o palancas, y un 201 debe significar que la orden existe, consumió su cupo una vez y llegará al ERP. Hubo releases que devolvían número de orden sin registrar la orden.

## Decisiones y alternativas descartadas

| Decisión | Descartado | Por qué |
|---|---|---|
| La cotización guarda su desglose y se confirma por `quoteId` | Recalcular al confirmar | Recalcular puede dar otro total |
| Combo, escala, obsequio, impuestos y crédito, en ese orden | Escala primero, o lo más favorable | Una unidad recibiría dos descuentos, y lo más favorable no se explica fácil |
| Escala marginal por tramos | Aplicar el tramo a todas las unidades | Evita saltos de precio al cruzar un tramo |
| `BigDecimal` y redondeo por línea según la moneda | Redondear el total | El total suma las líneas, como la factura |
| Orden, cupo, clave de idempotencia y eventos en una transacción | Publicar directo o 2PC | Publicar directo pierde eventos y ni el ERP ni el push soportan 2PC |
| Outbox con relay, al menos una vez y deduplicación por `eventId` | Broker de mensajería | Más infraestructura con la misma garantía |
| Consumir el cupo al confirmar, con un `UPDATE` condicional | Reservarlo al cotizar | Los carritos abandonados bloquearían cupo |
| `Idempotency-Key` obligatoria | Opcional | Sin clave, un reintento crea otro pedido |
| Spring MVC y JPA con SQL explícito para bloqueos | WebFlux | El problema es de consistencia, no de I/O |
| Monolito con puertos y adaptadores | Microservicios | Un solo contexto y una transacción local |
| PostgreSQL real en las pruebas | Base en memoria | Solo la base real prueba los bloqueos |

## Qué no se implementó y por qué

- **Autenticación.** La resuelve el gateway, y la API recibe la identidad en cabeceras.
- **Integraciones reales con el ERP y el push.** Los adaptadores ya tienen el contrato y escriben en el log.
- **Cancelación y rechazo del ERP.** Exigen devolver el cupo y acordar ese flujo con el ERP.
- **Sincronización del cupo.** Depende de cómo publique el ERP los abonos y ajustes.

## Qué revisaría en un code review

- `double`, `float` o `new BigDecimal(double)` en montos, y montos comparados con `equals`.
- Redondeos fuera del descuento de escala por línea, el total del combo y el impuesto de cada línea.
- Llamadas externas antes del commit, o al ERP sin timeout dentro del relay.
- Consultas de negocio sin `tenant_id`, y bloqueos en otro orden que clave, cotización y cupo.
- Valores esperados de las pruebas copiados de la salida del propio código.

## Riesgo y mitigación

El cupo local es una copia del cupo del ERP y puede divergir por abonos o ajustes manuales. Cada consumo queda como un movimiento ligado a la orden, así que un proceso diario puede comparar ambos saldos y registrar la diferencia como ajuste. Mientras tanto, el ERP sigue siendo la autoridad.
