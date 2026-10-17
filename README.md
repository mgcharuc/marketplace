# Marketplace B2B: motor de pedidos

API para cotizar y confirmar pedidos de tenderos a distribuidores de bebidas, en varios países y marcas.

## Cómo correrlo

### Requisitos

- JDK 21. Con [SDKMAN](https://sdkman.io): `sdk env install` instala y activa la versión fijada en `.sdkmanrc`. La activación solo dura en esa terminal; en cada terminal nueva ejecute `sdk env` dentro del proyecto, o active `sdkman_auto_env=true` en `~/.sdkman/etc/config` para que se aplique al entrar a la carpeta. Con un JDK anterior, Maven falla con `has been compiled by a more recent version of the Java Runtime`.
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

Con Docker no se requiere configuración adicional. Con Podman rootless (Linux/WSL), active el socket compatible con la API de Docker e indique su ubicación a Testcontainers; sin estos pasos, las pruebas de integración se omiten:

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

Antes de publicar una versión se valida completa: tramos de escala crecientes, precio de combo por debajo del valor de lista, referencias a SKU, familias y clases tributarias que existan, precios en la escala de la moneda, reglas que compiten con la misma prioridad y vigencias coherentes. El error lista todas las violaciones juntas.

Las semillas están en `src/main/resources/seed/`. `tenants.json` define Colombia (COP, escala 0) y Ecuador (USD, escala 2), y tres tenants: `CO-MARCA-A`, `CO-MARCA-B` y `EC-MARCA-A`. Cada tenant tiene su versión inicial en `<tenant>.v1.json`. Un campo desconocido, uno obligatorio que falta o un país inexistente hacen fallar la carga con la ruta del campo.

## Política de redondeo

Los montos son `BigDecimal` envueltos en `Money`, que lleva su moneda. En el dominio no hay `double` ni `float`.

- Cada moneda define su escala y su modo de redondeo, `HALF_UP` o `HALF_EVEN`. La escala es la de la práctica comercial, que puede no coincidir con la oficial.
- `Money` completa con ceros un valor con menos decimales que la moneda (`2.3` pasa a `2.30` en USD) y rechaza uno con más. Nunca redondea por su cuenta.
- Los cálculos intermedios, como multiplicar por una tasa, devuelven `BigDecimal` con precisión completa. `Money.round` es el único paso de un valor intermedio a un monto.
- Para repartir un monto en partes proporcionales se usa el método del mayor residuo (`Allocation.split`). Cada cuota se trunca a la escala de la moneda y las unidades mínimas que sobran van, de a una, a las partes con mayor fracción descartada. Si dos partes empatan, gana el SKU menor o el tramo que empieza antes. La suma de las cuotas siempre es el monto repartido.
