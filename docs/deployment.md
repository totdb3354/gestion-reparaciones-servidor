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
- `PEDIDOS_SHIPPYPRO_CANALES` (por defecto vacio): a que canal va cada marketplace de ShippyPro, `"<marketplace>:AMAZON,<marketplace>:AMAZON_STOCKROOM,..."`. Un marketplace por canal salvo Amazon: dos marketplaces en el mismo canal con el mismo numero de pedido compartirian filas.
- `PEDIDOS_HISTORICO_HOJA` (por defecto vacio): identificador en Google de la hoja de pedidos de la que se carga el historico (solo lectura, con la cuenta de `NSYS_DRIVE_CREDENCIALES`, que tiene que tener acceso a la hoja). Vacio, `POST /api/pedidos/historico` responde `400`. Nunca va al repo.
- `PEDIDOS_HISTORICO_PESTANA` (por defecto `PEDIDOS 2026`): la pestana de esa hoja.

### Pedidos como la hoja: orden de despliegue

- **La migracion `sql/migracion-pedidos-tabla.sql` se aplica ANTES de desplegar este servidor**, y se sigue solo si su comprobacion final da `CREADAS = 3` y `COLUMNAS = 3`. Sin las tres columnas nuevas de `Nsys_producto` (`SKU_AMAZON_ESIM`, `SKU_PARTICULARES`, `SKU_BACKIPHONE`) el servidor nuevo falla con «Unknown column»: dejan de funcionar Stock de telefonos y todo lo que traduce o valora un movil, y cada importacion de NSYS termina en error. La migracion es aditiva: el servidor anterior sigue funcionando con ella puesta (vuelta atras sin quitar nada).
- La primera importacion de NSYS tras desplegar recarga el maestro de Drive una vez, aunque no haya cambiado, para rellenar los SKU de los canales; es lo esperado.
- `PEDIDOS_LECTURA_ACTIVA` se queda en `false` en produccion hasta el corte: encenderla antes crea filas vivas en paralelo a la hoja. La carga del historico y el traspaso de IMEI son una herramienta a mano (`POST /api/pedidos/historico`, solo ADMIN; ver `docs/api_contract.md`): no se encienden solos y se pueden repetir. Lee la hoja entera (unas 12 peticiones de 5000 filas a Google) y despues escribe todo en una sola transaccion, con las vueltas de lectura de ShippyPro detenidas esos segundos (se saltan y vuelven a los 5 minutos); se espera menos de un minuto en total.
- Con la lectura encendida, una fila solo se cierra cuando su pedido falta en dos lecturas buenas seguidas (los envios se ven grises unos 5 minutos mas tarde), y un pedido que vuelve a estar pendiente reabre sus filas con su IMEI.

## Paso a paso operativo

El procedimiento operativo (VM, credenciales, dominios) está fuera de este repo, en la guía privada del equipo.
