# ADR 001. Cálculo determinista y confirmación atómica de pedidos

**Estado:** aceptado, 2026-10-19.

**Contexto.** El tendero debe pagar lo cotizado aunque cambien precios o palancas, y un 201 debe significar que la orden existe, consumió su cupo una vez y va a llegar al ERP. Hubo releases que devolvían número de orden sin dejar la orden registrada.

## Decisiones y alternativas descartadas

| Decisión | Descartado | Por qué |
|---|---|---|
| La cotización guarda su desglose y se confirma por `quoteId` | Recalcular al confirmar | Recalcular puede dar otro total |
| Aplicar combo, escala, obsequio, impuestos y crédito, en ese orden | Escala primero, o buscar lo más favorable | Una unidad recibiría dos descuentos, y lo más favorable es difícil de explicar |
| Escala marginal por tramos | Aplicar el tramo a todas las unidades | Evita saltos de precio al cruzar un tramo. El otro modo sigue disponible |
| `BigDecimal` y redondeo por línea según la moneda | Redondear el total | El total es la suma de las líneas, como en la factura |
| Orden, cupo, clave de idempotencia y eventos en una transacción | Publicar directo o 2PC | Publicar directo pierde eventos y ni el ERP ni el push soportan 2PC |
| Outbox con relay, al menos una vez y deduplicación por `eventId` | Broker de mensajería | Suma infraestructura y no mejora la garantía |
| Consumir el cupo al confirmar, con un `UPDATE` condicional | Reservarlo al cotizar | Los carritos abandonados bloquearían cupo |
| `Idempotency-Key` obligatoria | Opcional | Sin clave, un reintento crea otro pedido |
| Spring MVC y JPA con SQL explícito para bloqueos | WebFlux | El problema es de consistencia, no de I/O |
| Monolito con puertos y adaptadores | Microservicios | Un solo contexto y una transacción local |
| PostgreSQL real en las pruebas | Base en memoria | Solo la base real prueba bloqueos y atomicidad |

## Qué no se implementó y por qué

Autenticación, integraciones reales con el ERP y el push, sincronización del cupo con el ERP y lo que pasa después de confirmar. Cada una depende de un contrato externo que el enunciado no da. El detalle está en el README.

## Qué revisaría en un code review

- `double`, `float` o `new BigDecimal(double)` en montos, y montos comparados con `equals`.
- Redondeos fuera de los tres puntos que define el README.
- Llamadas a sistemas externos antes del commit de la confirmación.
- Consultas de negocio sin `tenant_id`.
- Bloqueos tomados en otro orden que clave de idempotencia, cotización y cupo.
- Valores esperados copiados de la salida del propio código.

## Riesgo y mitigación

El cupo local es una réplica del cupo del ERP y puede divergir por abonos o ajustes manuales. Cada consumo queda registrado como un movimiento de la orden, lo que permite conciliar. El siguiente paso es sincronizar el cupo con el ERP.
