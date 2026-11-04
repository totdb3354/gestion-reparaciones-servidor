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
- Lectura de pedidos (0.9.26; la lectura y el cruce de «Pedidos», cada 5 minutos, con las credenciales de Google de `NSYS_DRIVE_CREDENCIALES`). Solo se documentan los nombres; los valores van en el entorno de la VM, nunca en el repo:
  - `PEDIDOS_LECTURA_ACTIVA` (por defecto `false`): enciende la tarea (`TareaLecturaPedidos`); apagada, el servidor no lee nada y la línea de pedido de Asignaciones no consulta.
  - `PEDIDOS_SHIPPYPRO_CLAVE` (por defecto vacío): ruta, dentro del contenedor, del fichero con la clave de ShippyPro. Se lee la primera vez que hace falta, no al arrancar.
  - `PEDIDOS_SHIPPYPRO_CANALES` (por defecto vacío): a qué canal va cada marketplace de ShippyPro, `"<marketplace>:AMAZON,<marketplace>:AMAZON_STOCKROOM,…"`. Vacío, ShippyPro se registra como RECHAZADA y no se aplica nada.
  - `PEDIDOS_PEDIDOS2026_HOJA` (por defecto vacío): identificador de la hoja de Google de PEDIDOS 2026. Vacío, esa fuente es RECHAZADA.
  - `PEDIDOS_PEDIDOS2026_PESTANA` (por defecto `PEDIDOS 2026`): pestaña de esa hoja.
  - `PEDIDOS_SIMULADOR_HOJA` (por defecto vacío): identificador de la hoja de Google del Simulador. Vacío, esa fuente es RECHAZADA.
  - `PEDIDOS_SIMULADOR_PESTANA` (por defecto `Registro actividad`): pestaña de esa hoja.
  - `PEDIDOS_FILAS` (por defecto `4000`): cuántas filas finales se leen de cada hoja. Si no llegan a la ventana del Simulador, la lectura es INCOMPLETA y avisa de subir este valor.
  - `PEDIDOS_SIMULADOR_DIAS` (por defecto `14`): días de la ventana del Simulador; un pedido más antiguo se cierra como fuera de ventana y la vista lo oculta salvo que se pidan los viejos.
  - Antes de encender la lectura hay que aplicar a mano `sql/migracion-pedidos.sql` (tres tablas y tres columnas de `Nsys_producto`); es aditiva y el servidor anterior funciona con ella puesta.

## Paso a paso operativo

El procedimiento operativo (VM, credenciales, dominios) está fuera de este repo, en la guía privada del equipo.
