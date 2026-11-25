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
- `PEDIDOS_SINCRONIZADOS_CLAVE` (por defecto vacio): claves de maquina que pueden ESCRIBIR en `POST /api/pedidos/sincronizados` (el aviso del plugin de la web: ya van estos moviles en el pedido; cabecera `X-API-Key`). Ajuste aparte del de los vendidos (que solo lee): una clave solo de `PEDIDOS_VENDIDOS_CLAVE` da `401` en esa ruta, y una clave puede estar en los dos ajustes (el plugin usa la misma para leer y para avisar). Varias separadas por comas; las de menos de 32 caracteres se ignoran (aviso al arrancar con cuantas, nunca cuales); si no queda ninguna valida, o esta vacio, la ruta responde `404` para todos. Hexadecimal, 32 caracteres o mas; una distinta en produccion y en preprod; nunca al repo ni a la documentacion.
- `PEDIDOS_ASOCIACIONES_CLAVE` (por defecto vacio): claves de maquina de `POST /api/pedidos/asociaciones` (las asociaciones IMEI -> pedido de los libros de ventas de Amazon; cabecera `X-API-Key`). Ajuste propio: las claves de `PEDIDOS_VENDIDOS_CLAVE` o de `PEDIDOS_SINCRONIZADOS_CLAVE` dan `401` en esa ruta (salvo que la misma clave este tambien aqui). Varias separadas por comas; las de menos de 32 caracteres se ignoran (aviso al arrancar con cuantas, nunca cuales); si no queda ninguna valida, o esta vacio, el `POST` responde `404` para todos (el `GET` de la misma ruta es de usuario y sigue funcionando). Hexadecimal, 32 caracteres o mas; una distinta en produccion y en preprod; nunca al repo ni a la documentacion. Antes de desplegar el servidor que la usa se aplica `sql/migracion-pedidos-asociacion.sql`.
- `PEDIDOS_BOT_CLAVE` (por defecto vacio): claves de maquina de `GET /api/bot/devoluciones` y `POST /api/bot/movimientos` (el bot de stock de NSYS: lee la lista de IMEI a devolver y escribe lo que mueve; cabecera `X-API-Key`). Ajuste propio: las claves de `PEDIDOS_VENDIDOS_CLAVE`, `PEDIDOS_SINCRONIZADOS_CLAVE` o `PEDIDOS_ASOCIACIONES_CLAVE` dan `401` en esas rutas (salvo que la misma clave este tambien aqui). Varias separadas por comas; las de menos de 32 caracteres se ignoran (aviso al arrancar con cuantas, nunca cuales); si no queda ninguna valida, o esta vacio, las dos rutas responden `404` para todos. Hexadecimal, 32 caracteres o mas; una distinta en produccion y en preprod; nunca al repo ni a la documentacion. **Antes de desplegar el servidor que la usa se aplica `sql/migracion-bot-movimiento.sql` (comprobacion `COLUMNAS = 10` e `INDICES = 2`). Crea ademas dos indices de `Pedido_fila_cambio` (`IMEI_ANTERIOR`, `IMEI_NUEVO`): se crean en linea y son aditivos.**
- `PEDIDOS_LIBROS_ACTIVA` (por defecto `false`): enciende el lector de los dos libros de ventas de Amazon desde Drive, cada 10 minutos (el primero, 2,5 minutos despues de arrancar). Por canal con libro configurado pide a Drive la fecha de modificacion del fichero; si no cambio desde su ultima lectura buena, no hace nada; si cambio, lo descarga a un temporal (se borra al acabar), lee en streaming solo la pestana `Paso 1. Meter IMEI` (cabecera en la fila 6; columnas `order id`, `purchase-date` e `IMEI`) y aplica las asociaciones de las filas con fecha de compra de los ultimos 30 dias, como el `POST /api/pedidos/asociaciones`. Si falla (Drive, falta la pestana o una cabecera, libro ilegible) no toca nada, apunta el error en la pasada del canal (lo devuelve `GET /api/pedidos/asociaciones`) y lo reintenta en la vuelta siguiente. Antes de encenderlo se aplica `sql/migracion-pedidos-asociacion.sql` (con las tres columnas del lector).
- `PEDIDOS_LIBRO_AMAZON` y `PEDIDOS_LIBRO_STOCKROOM` (por defecto vacios): identificadores de fichero en Drive de los libros de ventas de Amazon (canal `AMAZON`) y de Amazon Stockroom (canal `AMAZON_STOCKROOM`); por identificador, no por nombre (hay copias viejas con nombres parecidos). Sin identificador, ese canal no se lee. Se leen con la cuenta de servicio de `NSYS_DRIVE_CREDENCIALES`, que tiene que poder leer los dos ficheros (compartidos con ella como lector, o miembro de la unidad compartida donde estan; las llamadas a Drive admiten unidades compartidas). Nunca van al repo.
- `PEDIDOS_VENDIDOS_CLAVE` (por defecto vacio): clave de maquina con la que el bot de stock llama a `GET /api/pedidos/vendidos` (cabecera `X-API-Key`). Admite varias claves separadas por comas (una por consumidor, todas con el mismo permiso). Las de menos de 32 caracteres se ignoran (un aviso al arrancar con cuantas, nunca cuales); si no queda ninguna valida, o esta vacia, esa ruta responde `404` para todos. Minimo 32 caracteres cada una; generarla en hexadecimal (solo ASCII). Una clave distinta en produccion y en preprod; solo vale para esa ruta y nunca va al repo ni a la documentacion.
- `PEDIDOS_RESERVA_AL_BOT` (ajuste `pedidos.reserva.al-bot`, por defecto `false`): con `true`, `GET /api/pedidos/vendidos` sirve tambien los IMEI reservados de filas vivas sin IMEI (fecha = la de la reserva). Encenderlo solo cuando exista la lista de devoluciones del bot (spec reserva §6).
- **La migracion `sql/migracion-pedidos-reserva.sql` se aplica ANTES de desplegar el servidor con la reserva de IMEI**: el SQL nuevo selecciona `IMEI_RESERVADO`, `RESERVADA_EN` y `SIN_STOCK_EN`, y sin ellas cada lectura de Pedidos y cada lectura de ShippyPro falla con «Unknown column». Se sigue solo tras comprobar que existen las 3 columnas y el indice `idx_fila_reservado`. Es aditiva (el servidor anterior sigue funcionando con ella puesta). Vuelta atras: primero el codigo anterior y despues `sql/migracion-pedidos-reserva-atras.sql`.
- `PEDIDOS_HISTORICO_PESTANA` (por defecto `PEDIDOS 2026`): la pestana de esa hoja.
- `PEDIDOS_HISTORICO_MINIMO` (por defecto `1000`): filas utiles minimas que tiene que traer la hoja; con menos, la carga se aborta sin tocar nada (guarda contra una lectura vacia o cortada).

- `SPRING_TASK_SCHEDULING_POOL_SIZE=2` (recomendado cuando se enciende la lectura de pedidos o el lector de los libros; no hay valor por defecto en el repo, Spring usa 1): con un solo hilo para todas las tareas programadas, una vuelta lenta de ShippyPro (hasta unos 4 minutos con la API colgada) retrasa la importacion de NSYS y al reves; con 2 no se pisan.

### Pedidos como la hoja: orden de despliegue

- **La migracion `sql/migracion-pedidos-tabla.sql` se aplica ANTES de desplegar este servidor**, y se sigue solo si su comprobacion final da `CREADAS = 3` y `COLUMNAS = 3`. Sin las tres columnas nuevas de `Nsys_producto` (`SKU_AMAZON_ESIM`, `SKU_PARTICULARES`, `SKU_BACKIPHONE`) el servidor nuevo falla con «Unknown column»: dejan de funcionar Stock de telefonos y todo lo que traduce o valora un movil, y cada importacion de NSYS termina en error. La migracion es aditiva: el servidor anterior sigue funcionando con ella puesta (vuelta atras sin quitar nada).
- La primera importacion de NSYS tras desplegar recarga el maestro de Drive una vez, aunque no haya cambiado, para rellenar los SKU de los canales; es lo esperado.
- `PEDIDOS_LECTURA_ACTIVA` se queda en `false` en produccion hasta el corte: encenderla antes crea filas vivas en paralelo a la hoja. La carga del historico y el traspaso de IMEI son una herramienta a mano (`POST /api/pedidos/historico`, solo ADMIN; ver `docs/api_contract.md`): no se encienden solos y se pueden repetir. Lee la hoja entera (unas 12 peticiones de 5000 filas a Google) y despues escribe todo en una sola transaccion, con las vueltas de lectura de ShippyPro detenidas esos segundos (se saltan y vuelven a los 5 minutos); se espera menos de un minuto en total.
- Con la lectura encendida, una fila solo se cierra cuando su pedido falta en dos lecturas buenas seguidas (los envios se ven grises unos 5 minutos mas tarde), y un pedido que vuelve a estar pendiente reabre sus filas con su IMEI.

## Paso a paso operativo

El procedimiento operativo (VM, credenciales, dominios) está fuera de este repo, en la guía privada del equipo.
