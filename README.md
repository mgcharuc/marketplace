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
