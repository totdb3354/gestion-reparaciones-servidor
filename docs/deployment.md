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
- `NSYS_IMPORTACION_ACTIVA` (0.9.17; por defecto `false`): enciende la importación del espejo de NSYS cada 5 minutos.
- `NSYS_DRIVE_CARPETA_ITEMS` y `NSYS_DRIVE_MAESTRO`: identificadores en Drive de la carpeta de los ficheros Items y del maestro de productos.
- `NSYS_DRIVE_CREDENCIALES`: ruta, dentro del contenedor, del JSON de la cuenta de servicio de Google. Es un fichero montado en solo lectura; nunca va al repo.
- `NSYS_HISTORICO_ACTIVO` (por defecto `false`): carga el histórico de exportaciones de 19:00 a 7:00, un fichero cada 5 s; cuando termina, no hace nada.
- `PEDIDOS_LECTURA_ACTIVA` (por defecto `false`): enciende la lectura de pedidos de ShippyPro; apagada, el servidor no lee nada.
- `PEDIDOS_SHIPPYPRO_CLAVE` (por defecto vacio): ruta, dentro del contenedor, del fichero con la clave de ShippyPro. Nunca va al repo.
- `PEDIDOS_SHIPPYPRO_CANALES` (por defecto vacio): a que canal va cada marketplace de ShippyPro, `"<marketplace>:AMAZON,<marketplace>:AMAZON_STOCKROOM,..."`.

## Paso a paso operativo

El procedimiento operativo (VM, credenciales, dominios) está fuera de este repo, en la guía privada del equipo.
