# Despliegue — Gestión Reparaciones (servidor)

> Nota: la guía anterior de despliegue manual (JAR bajo systemd, MariaDB en el host, puerto 8080 abierto) se retiró el 2026-09-15 por obsoleta.

El servidor se despliega en contenedores Docker, junto a la web, y ya no se ejecuta como JAR suelto sobre un host con MariaDB nativo.

## Imagen

El `Dockerfile` de este repo construye la imagen en dos fases: `eclipse-temurin:17-jdk` con Maven para compilar el JAR, y `eclipse-temurin:17-jre` para ejecutarlo.

## Ficheros de despliegue de referencia

El `docker-compose` y la configuración de `nginx` que orquestan el servidor junto a la web y la base de datos viven en el repo `gestion-reparaciones-web`, carpeta `deploy/`.

## Configuración

La configuración se inyecta por variables de entorno, sin valores por defecto en el repo:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `JWT_EXPIRATION`
- `SERVER_ERROR_INCLUDE_MESSAGE`
- `SPRINGDOC_SWAGGER_UI_ENABLED`

## Paso a paso operativo

El procedimiento operativo (VM, credenciales, dominios) está fuera de este repo, en la guía privada del equipo.
