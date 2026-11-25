# Contrato de la API

Desde 2026-09 el contrato se **genera** con springdoc: `GET /v3/api-docs` (requiere JWT) devuelve el
OpenAPI de todos los controllers. La app web guarda un snapshot en `gestion-reparaciones-web/api/openapi.json`
y genera sus tipos TypeScript de él (`npm run api:types`). Este fichero ya no se mantiene a mano.

Nombres de esquema: los records anidados en un controller se publican como `<Controller sin
sufijo><Record>` (p. ej. `ClienteEditarRequest`) para que no colisionen entre controllers; los modelos
de `model/` conservan su nombre.

## Convenciones que el OpenAPI no expresa

- Bloqueo optimista: los endpoints de edición que reciben `updatedAt` (p. ej. `PUT /api/clientes/{id}`,
  `PATCH /api/clientes/{id}/activo`, y sus equivalentes en reparaciones, componentes, usuarios,
  proveedores...) responden `409 Conflict` con `{"message": ...}` si el registro fue modificado por
  otro usuario; el cliente recarga y avisa. `409` también para conflictos de negocio (nombre duplicado,
  cliente con teléfonos asociados al borrar).
- `422 Unprocessable Entity` con `{"message": ...}` para reglas de negocio rechazadas (p. ej.
  contraseña actual incorrecta en `PATCH /api/auth/cambiar-password`); `400` para cuerpos inválidos.
- `?tecnico=` (entero, opcional) en las listas del taller (`GET /api/reparaciones/historial`,
  `/api/reparaciones/asignaciones`, `/api/glass/historial`, `/api/glass/asignaciones`,
  `/api/pulidos/historial`, `/api/pulidos/asignaciones`) y en `GET /api/reparaciones/pendientes/contadores`
  pasa por `FiltroTecnico`: un TECNICO solo recibe lo suyo (sin parámetro o con su propio `idTec`) y, si
  pide otro técnico, `403` con el motivo "Solo puedes consultar tus propios trabajos"; SUPERTECNICO y
  ADMIN filtran libremente (sin parámetro, todos). Detalle en `docs/autorizacion_endpoints.md`.
- Las seis listas del taller (`GET /api/reparaciones/historial`, `/api/reparaciones/asignaciones`, `/api/glass/historial`,
  `/api/glass/asignaciones`, `/api/pulidos/historial`, `/api/pulidos/asignaciones`) traen en `ReparacionResumen` la `capacidad`
  (de `Nsys_movil`) y el `color` que vale (lo del taller vigente o el de NSYS) de la fila más reciente del IMEI en el espejo de
  NSYS (0.9.21). Son `null` si el IMEI no está en el espejo, si la lectura falla (la lista sale igual y se apunta en el log) y en
  las demás rutas que devuelven `ReparacionResumen`. `modelo` sigue siendo el código de `Telefono.MODELO`.
- Desde la 0.9.24, esas seis listas traen también `esEsim` en `ReparacionResumen` (booleano, no nulo, el último campo): si NSYS
  escribe «eSIM» en el producto (`Nsys_movil.ES_ESIM`), de la misma fila que la `capacidad`; `false` fuera del espejo, si la
  lectura falla y en las demás rutas que devuelven `ReparacionResumen`. Lo traen igual la ficha
  (`GET /api/stock-telefonos/{imeiSn}`), el buscador (`GET /api/stock-telefonos/buscar`), «Comprobar»
  (`POST /api/stock-telefonos/comprobar`; un IMEI no encontrado, `false`) y las filas de «Cambio de color», «Cambio de grado»,
  «Revisar a mano» y «Sin grado en NSYS» de las tarjetas de «Por pasar a NSYS» (un aviso sin móvil en el espejo, `false`). El
  stock (`GET /api/stock-telefonos`) ya lo traía.
- Desde la 0.9.25 («Qué revisar» en Stock de teléfonos, SUPERTECNICO y ADMIN): `POST /api/stock-telefonos/que-revisar/excel`
  recibe `{hoja, columnas, filas}` (textos) y responde el xlsx para imprimir: cabecera en negrita repetida en cada página,
  una página de ancho, bordes finos y anchos según el contenido; una celda de datos que sea un entero de 1 a 9 cifras sin ceros a
  la izquierda va como número (lo demás, texto) y la hoja sale en vertical si la suma de anchos es 90 caracteres o menos, apaisada si pasa. No sabe nada de la lista. `400` con el motivo si la
  hoja está vacía, pasa de 31 caracteres, lleva `[ ] : * ? / \` o empieza o acaba con un apóstrofo; si no hay de 1 a 12 columnas o una no tiene nombre (o es solo espacios); si pasa
  de 5000 filas; si una fila no tiene tantas celdas como columnas o lleva una nula, o si un texto pasa de 200 caracteres.
- Desde la 0.9.26, cada fila de «Cambio de color» (`FilaColorNsys` en `GET /api/stock-telefonos/por-pasar/tarjetas`) trae
  `caja` (nullable, el último campo): la caja de NSYS del lote del móvil tal cual (`Nsys_movil.LOCATION`); `null` si no tiene.
  La web la enseña en la columna «Caja» y la escribe en «⬇ Descargar cajas», cuyo Excel hace `POST
  /api/stock-telefonos/que-revisar/excel`. La plantilla de color no cambia.
- Lista de IMEI vendidos para el bot de stock (no es una ruta de usuario): `GET /api/pedidos/vendidos?dias=7&canal=` con la cabecera
  `X-API-Key` → lista JSON en la raíz de `Vendido` `{imei, sku, canal, pedido, fechaImei}` (sin `plataforma`: en los pedidos web es el
  nombre del cliente). `canal` es opcional: un nombre del enum `Canal` (`AMAZON`, `AMAZON_STOCKROOM`, `DISTRIBUIDORES`,
  `PARTICULARES`, `BACKIPHONE`) filtra por el canal de la fila; desconocido, `400`; ausente, todos. El filtro va antes de quitar
  repetidos. Una por IMEI (sin repetidos;
  si está en dos filas, la del cambio más reciente). `sku`, `canal` y `pedido` son nullable; `fechaImei` es la hora de Madrid (sin
  zona) del último cambio de IMEI de la fila. Entran las filas `SHIPPYPRO` pendientes o enviadas (no el histórico) y las
  `NO_PENDIENTE` (cerradas sin envío) solo si su último cambio de IMEI es posterior a su cierre (`CERRADA_EN`): alguien tecleó el IMEI
  sabiendo que el pedido ya no estaba pendiente, así que el móvil está vendido; una cerrada que ya tenía el IMEI antes de cerrarse
  (pedido cancelado) no sale; con IMEI de 15 cifras cuyo último apunte de `Pedido_fila_cambio` cae en hoy o en los `dias - 1` días de Madrid
  anteriores (no se mira `Pedido_fila.FECHA`); como mucho 5000. Pedidos web juntados en una sola etiqueta: una fila de
  `DISTRIBUIDORES`, `PARTICULARES` o `BACKIPHONE` cerrada sin envío propio (`CERRADA_EN` puesto, `ENVIO_FECHA` nulo) que el plugin de
  la web ha sincronizado (`SINCRONIZADA_EN` puesto) y sin marca sale **aunque el IMEI se pusiera antes del cierre**; para ella la fecha
  de la ventana y su `fechaImei` es la más reciente entre su último apunte (`IMEI` o `MARCA_QUITADA`) y `SINCRONIZADA_EN` (una
  sincronización tardía entra en la ventana aunque el IMEI sea antiguo; si el plugin la vuelve a sincronizar, vuelve a entrar). Sin
  sincronizar sigue sin salir; Amazon no cambia; las demás filas no miran `SINCRONIZADA_EN`. `dias`: entero de 1 a 31 (por defecto 7); fuera de rango o no
  numérico, `400`. Autenticación: solo la clave (ajuste `pedidos.vendidos.clave`, variable `PEDIDOS_VENDIDOS_CLAVE`): ajuste
  admite varias claves separadas por comas (una por consumidor, todas con el mismo permiso; las de menos de 32 caracteres se
  ignoran); sin ninguna válida, `404` para todos; sin cabecera o con otra clave, `401` (comparación en tiempo constante); un token de usuario, del rol que
  sea, no vale (`401`) y la clave no abre ninguna otra ruta.
- «Pedidos como la hoja» (SUPERTECNICO y ADMIN; el TECNICO recibe `403`), `/api/pedidos`:
  - `GET /api/pedidos/filas?desde=&hasta=&buscar=` → `ListaFilas` `{filas, hayMas}`, las filas en orden de hoja (la más antigua
    primero). `desde` y `hasta` son fechas ISO opcionales (por defecto, el último mes hasta hoy, hora de Madrid) y la lista trae
    además las filas pendientes (sin cerrar) anteriores al periodo. Con `buscar` (se recorta; menos de 2 caracteres, `400` "Escribe al menos 2
    caracteres") se busca en todo el histórico por plataforma, SKU, IMEI o pedido: como mucho 500 filas, las más recientes, y
    `hayMas` es `true` si había más. Cada `FilaVista` trae `estado` (`PENDIENTE`, `ENVIADA`, `NO_PENDIENTE` o `HISTORICO`, nunca
    nulo: `ENVIADA` si ShippyPro dio el pedido por enviado, `NO_PENDIENTE` si dejó de estar pendiente sin envío conocido),
    `editable` (`false` solo en el histórico) y, nullable, `envioFecha` (hora de Madrid), `transportista` y `seguimiento`. Trae
    además `impresaEn` (nullable, hora de Madrid: cuándo ShippyPro dio la etiqueta por impresa; `null` = sin imprimir) y
    `tieneEtiqueta` (booleano: la fila tiene el id del envío en ShippyPro; el id no se expone). El `estado` no cambia. Trae
    `nivel` (`OK`, `AVISO` o `MAL`), `motivo`, `skuMovil` y `caja` solo en las filas `PENDIENTE`, `NO_PENDIENTE` y `ENVIADA` sin imprimir (`impresaEn` nulo; con la etiqueta solo generada el móvil sigue en el almacén), con IMEI (el
    aviso se calcula al servir, no se guarda); en las demás son `null`. `sku`, `imei`, `pedido` y `fechaCompra` son nullable
    (las filas del histórico llevan los textos de la hoja tal cual). Trae también `canal` (nullable): el nombre del enum `Canal`
    (`AMAZON`, `AMAZON_STOCKROOM`, `DISTRIBUIDORES`, `PARTICULARES`, `BACKIPHONE`); en las filas de ShippyPro, el guardado; en las
    del histórico se deduce al servir de `plataforma` (recortada, sin distinguir mayúsculas: `AMZ` → `AMAZON`; `FNSK` o `FSNK` →
    `AMAZON_STOCKROOM`) y es `null` en todo lo demás (clientes, incidencias, `NP`, vacía). Trae además, nullable, `faltaEnviarEn` (hora de
    Madrid: cuándo una persona marcó que el móvil de la fila falta por enviar, en un envío parcial de un pedido de web; `null` = sin
    marca) y `sincronizadaEn` (la última vez que el plugin de la web avisó de que el pedido ya lleva ese móvil; `null` = nunca). El
    `estado` y el aviso no cambian: la web deriva de esos campos lo que enseña.
    `FilaVista` gana al final `fechaSalida` (ISO, nullable: último día para enviar; Amazon, Latest ship de ShippyPro; webs, entrada + 2 o 3
    días laborables; histórico, `null`) y `pais` (nullable).
    0.9.44: `FilaVista` gana al final `salidaDesde` (ISO, nullable): en las webs, primer día en que el pedido ya «sale hoy» (la entrada,
    o el primer laborable si entró en festivo o fin de semana); nulo en Amazon, en el histórico y sin `fechaSalida`. Desde la 0.9.44 la
    `fechaSalida` de las webs es el laborable siguiente a `salidaDesde`, sin mirar el servicio de envío (antes, entrada + 2 o 3 laborables).
    `FilaVista` gana al final `detalle` (nullable): `{sim, na, ms, dato, trabajos}` con `sim` `"ESIM"` (el móvil es eSIM y el pedido no),
    `"SIM_FISICA"` (al revés) o `null`; `na` y `ms` booleanos (el grado que cuenta es n/a; es un Ms); `dato` (nullable: lo que es el móvil
    para el motivo, sea modelo, capacidad, color o grado) y `trabajos` (lista de `TrabajoAbierto`, solo con motivo `EN_TALLER`; si no, vacía).
    `null` sin aviso o sin nada que decir. Motivos nuevos de `motivo`: `CAPACIDAD`, `COLOR` y `GRADO` (`COLOR_O_GRADO` solo queda si no se
    puede decir cuál); `MS` ya no sale como motivo: un Ms es un `AVISO` con `motivo` `null` y `detalle.ms` `true`. Una fila validada
    conserva `sim`, `na` y `ms` en `detalle` (el `dato` va `null` y `trabajos` vacía).
    Reserva de IMEI (0.9.48): `FilaVista` gana después de `detalle` `imeiReservado` (nullable: el IMEI que el ERP aparta para la fila; `null` = sin reserva; la fila
    sigue contando como sin IMEI) y `reservadaEn` (nullable, hora de Madrid sin zona: cuándo se reservó). `CambioImei.tipo` admite además
    `RESERVADO`, `RESERVA_CONFIRMADA`, `RESERVA_SUSTITUIDA`, `RESERVA_QUITADA` y `SIN_STOCK`.
  - `GET /api/pedidos/filas/{id}/cambios` → lista de `CambioImei` `{usuario, fecha, imeiAnterior, imeiNuevo, tipo}` con el historial
    de la fila, del más reciente al más antiguo (hasta 200; `fecha` y hora de Madrid sin zona). `tipo` (campo añadido, nunca nulo; la web
    que no lo conoce lo ignora): `IMEI` (cambio de IMEI, los de siempre), `FALTA_ENVIAR` (se marcó que el móvil falta por enviar) o
    `YA_ENVIADO` (se quitó esa marca); en los dos últimos `imeiAnterior` e `imeiNuevo` son `null`. `usuario` (nombre de usuario),
    `imeiAnterior` e `imeiNuevo` son nullable (`usuario` nulo en el traspaso desde la hoja y cuando quitó la marca el plugin de la web;
    `imeiNuevo` nulo = se quitó). `404` si la
    fila no existe; lista vacía si es del histórico. SUPERTECNICO y ADMIN.
  - `GET /api/pedidos/filas/{id}/recomendados` → lista de `RecomendadorCandidato` `{imei, sku, caja, nivel}` (hasta cinco; `sku`
    y `caja` nullable). `404` si la fila no existe; lista vacía si es del histórico o si el maestro no conoce su SKU (las enviadas y las no pendientes
    sí dan candidatos). No ofrece los móviles puestos en otra fila pendiente o no pendiente, ni en una enviada cuyo móvil NSYS
    aún no da por vendido (sí el de la propia fila).
  - `GET /api/pedidos/filas/{id}/etiqueta` → el PDF de la etiqueta que ShippyPro ya generó (`200`, `application/pdf`,
    `Content-Disposition: inline; filename="etiqueta.pdf"`, `Cache-Control: no-store`). Solo lectura: no se guarda ni se imprime
    nada (ShippyPro `GetLabelUrl`, y se descarga el PDF de su CDN con `https` y host `shippypro.com`, sin redirecciones, tope de 8 MB).
    `404` si la fila no existe o si no tiene etiqueta («Este pedido no tiene etiqueta generada»: pendiente sin envío, histórico, cerrada
    sin envío); `502` «No se pudo obtener la etiqueta de ShippyPro. Inténtalo de nuevo.» si ShippyPro falla, no da etiqueta o la
    descarga falla, no es un PDF o es demasiado grande (nunca el texto de ShippyPro ni la URL). SUPERTECNICO y ADMIN.
  - `GET /api/pedidos/buscar` (el «Listado», sin parámetros) → un `.xlsx` (`200`) con tres hojas, en este orden: `AMZ` (canal `AMAZON`),
    `FNSK` (`AMAZON_STOCKROOM`) y `Webs` (`DISTRIBUIDORES`, `PARTICULARES` y `BACKIPHONE`), cada una con lo descrito abajo.
    Cuenta las filas de ShippyPro **sin IMEI** (vacío cuenta como sin IMEI) y **sin marca `CANCELADO`** (cualquier otra marca cuenta) que
    siguen por buscar: las vivas (sin cerrar) y las enviadas con «falta enviar»; no cuenta las cerradas sin envío, las enviadas sin falta,
    el histórico ni otros canales. `AMZ` y `FNSK`: dos tablas, `SKU | un país por columna | Total general` (países en orden alfabético y `—` al final
    si falta el país; SKU agrupados sin distinguir mayúsculas, con la forma más repetida; números como celdas numéricas, vacío donde es 0;
    fila «Total general» al pie): «Para hoy» (`fechaSalida` nula o hasta hoy, hora de Madrid) y, tras una fila en blanco, «Pueden esperar»
    (`fechaSalida` posterior a hoy). Una tabla sin móviles lleva su título y «No hay móviles que buscar». `Webs` va distinto (en las webs se envía
    todo en el día): **no se divide por fecha** y lleva un bloque por web en este orden: «Fonestockroom (distribuidores)», «Fonestore (particulares)»
    y «BackiPhone»; cada bloque: título en negrita, cabecera `Cliente | SKU | Uds | Entró`, una fila por cliente (columna `PLATAFORMA`; vacío =
    «(sin cliente)»), SKU (sin distinguir mayúsculas, forma más repetida) y día de entrada, con las unidades (`Uds` numérica), y una fila «Total» con
    la suma de `Uds`; «Entró» = la columna `FECHA` de la fila (día en que el ERP vio el pedido) como celda de fecha con formato `dd/mm/yyyy`; orden por
    «Entró» (lo más antiguo primero), cliente y SKU; una fila en blanco entre bloques; un bloque sin móviles lleva su título y «No hay móviles que
    buscar». Sin número de pedido, país ni envío. Cada hoja, A4 vertical, ajustada a una página de ancho. Cabeceras:
    `Content-Disposition: attachment; filename="Listado 18-11 09h30.xlsx"` (dd-MM y HHhmm de ahora, hora de Madrid; sin «:») y `X-Filas` (móviles
    contados entre las tres hojas: las dos tablas de AMZ y FNSK y las unidades de los bloques de Webs). SUPERTECNICO y ADMIN.
  - `PUT /api/pedidos/filas/{id}/imei` con `PeticionImei` `{imei, imeiAnterior?}` → `FilaVista` releída, con su aviso y su `canal`. `imei` nulo o en blanco
    lo quita; si no, 15 cifras (`400` "Un IMEI son 15 cifras"). `404` si la fila no existe; `409` "La fila es del histórico y no se edita" si es del
    histórico (las filas de ShippyPro se editan siempre, también las enviadas). `imeiAnterior` (opcional, nullable; la web vieja no lo manda): lo que la persona veía en la fila; ausente o `null` no comprueba nada, `""` espera la fila sin IMEI, 15 cifras espera ese IMEI (otra cosa: `400`). Si no coincide con el IMEI actual, `409` "Otra persona ha cambiado el IMEI de esta fila mientras tanto. Recarga y vuelve a intentarlo." sin escribir ni apuntar nada (comprobación y escritura atómicas, con la fila bloqueada). `409` si el IMEI ya está en otra fila de ShippyPro pendiente o enviada con «falta por enviar» (la propia fila, las pendientes con marca CANCELADO, las enviadas sin falta y el histórico no cuentan; no escribe nada): "Este IMEI ya está en el pedido X (fila del dd/mm)", o, si esa fila es de Amazon y no está asociada, "Este IMEI ya está en una fila de AMZ del dd/mm sin asociar" ("FNSK" en Stockroom). Cabecera opcional `Idempotency-Key`; el cambio queda apuntado con el usuario del token.
  - `PUT /api/pedidos/filas/{id}/falta-enviar` con `PeticionFaltaEnviar` `{falta}` (obligatorio; `null` o ausente `400`) → `FilaVista` releída.
    `true` marca el móvil de la fila como «falta enviar» (envío parcial de un pedido de web: la etiqueta salió con todos los móviles y este
    todavía no); `false` quita la marca. Marcar exige una fila de ShippyPro de un canal web (`DISTRIBUIDORES`, `PARTICULARES` o
    `BACKIPHONE`) con la etiqueta generada (`envioFecha` no nula); si no, `409` "Solo se puede marcar en un pedido de web con la etiqueta ya
    generada". `404` si la fila no existe; `409` "La fila es del histórico y no se edita" si es del histórico. Marcar una fila ya marcada o
    desmarcar una sin marca no hace nada (`200`, sin apunte nuevo); quitar la marca siempre se puede si la tiene. Poner y quitar la marca
    quedan en el historial (`tipo` `FALTA_ENVIAR` y `YA_ENVIADO`) con quién y cuándo; la fila guarda cuándo (`faltaEnviarEn`) y quién la
    puso. Comprobación y escritura atómicas, con la fila bloqueada. Cabecera opcional `Idempotency-Key`. SUPERTECNICO y ADMIN. La lectura
    de ShippyPro (marcar enviada o impresa) no toca la marca. Es independiente de las marcas de abajo.
  - `PUT /api/pedidos/filas/{id}/marca` con `PeticionMarca` `{marca, nota?, marcaAnterior?}` → `FilaVista` releída. `marca`: `ESPERA_STOCK`
    («En espera de stock»), `EN_REVISION` («En revisión»), `PENDIENTE_COLOR` («Pendiente de color») o `CANCELADO` («Cancelado»), o `null`
    para quitar la marca; otra cosa, `400`. Como mucho una marca por fila. `nota` (opcional, nullable): hasta 200 caracteres tras recortar
    espacios (más, `400`); vacía o en blanco = sin nota; se ignora al quitar. `marcaAnterior` (opcional, nullable; como `imeiAnterior`):
    la marca que la persona veía; `null` o ausente no comprueba nada, `""` espera la fila sin marca, una de las cuatro espera esa marca
    (otra cosa: `400`); si no coincide con la actual, `409` "Otra persona ha cambiado la marca de esta fila mientras tanto. Recarga y vuelve
    a intentarlo." sin escribir ni apuntar nada (comprobación y escritura atómicas, con la fila bloqueada). `404` si la fila no existe; `409`
    "La fila es del histórico y no se edita" si es del histórico. Poner otra marca, o la misma con otra nota, es un cambio (apunte `MARCA`
    nuevo); la misma marca con la misma nota, o `marca: null` en una fila sin marca, es un `200` sin apunte. Cabecera opcional
    `Idempotency-Key` (el registro incluye marca, nota y marcaAnterior). SUPERTECNICO y ADMIN (TECNICO `403`).
    `FilaVista` gana al final `marca`, `marcaNota`, `marcaEn` (hora de Madrid) y `marcaPor` (nombre de usuario de quien la puso), todos
    nullable (nulos sin marca; `marcaNota` también si la marca no lleva nota). Efectos en el servidor: poner un IMEI (no nulo) con
    `PUT .../imei` en una fila `ESPERA_STOCK` quita la marca en la misma transacción y apunta `MARCA_QUITADA` con detalle
    `"ESPERA_STOCK — al poner el IMEI"`; las demás marcas no se tocan al poner IMEI, y quitar el IMEI no toca ninguna. La lectura de
    ShippyPro no toca las marcas.
  - Historial (`GET /api/pedidos/filas/{id}/cambios`): `CambioImei` gana `detalle` (nullable; nulo en `IMEI`, `FALTA_ENVIAR` y `YA_ENVIADO`).
    Tipos nuevos de `tipo`: `MARCA` (se puso o cambió la marca; `detalle` `"<MARCA>"` o `"<MARCA> — <nota>"`, `imeiAnterior` e `imeiNuevo`
    nulos) y `MARCA_QUITADA` (se quitó; `detalle` = la marca que tenía, con `"<MARCA> — al poner el IMEI"` si la quitó el ERP al poner un
    IMEI, en cuyo caso el usuario es quien puso el IMEI).
  - Validar SKU (spec `2026-11-16-pedidos-validar-sku-design.md`): `PUT /api/pedidos/filas/{id}/validacion` con `PeticionValidacion`
    `{validar, imeiAnterior?}` → `FilaVista` releída. `validar` obligatorio (`true` da por bueno el aviso de SKU del IMEI de la fila, `false`
    quita la validación; ausente o nulo, `400`). `imeiAnterior` (opcional, nullable) como en `PUT .../imei`: `null` no comprueba, `""` espera
    la fila sin IMEI, 15 cifras espera ese IMEI (otra cosa: `400`); si no coincide con el IMEI actual, `409` "Otra persona ha cambiado el IMEI de
    esta fila mientras tanto. Recarga y vuelve a intentarlo.". Validar exige que la fila tenga IMEI (si no, `409` "La fila no tiene IMEI: no hay
    nada que validar") y que su aviso actual sea de SKU: `motivo` `MODELO`, `CAPACIDAD`, `COLOR`, `GRADO`, `COLOR_O_GRADO`, `GRADO_NO_VENDIBLE` o `SKU_DESCONOCIDO` (con
    `NO_EN_NSYS`, `EN_OTRA_FILA`, cualquier aviso ámbar o sin aviso, `409` "Solo se puede validar un aviso de SKU ..."). `404` si la fila no
    existe; `409` "La fila es del histórico y no se edita" si es del histórico. Validar lo ya validado para ese IMEI, o quitar lo no
    validado, es un `200` sin apunte; quitar una validación que ya no vale (de otro IMEI) la borra sin apunte. Atómico, con la fila
    bloqueada. Cabecera opcional `Idempotency-Key` (el registro incluye validar e imeiAnterior). SUPERTECNICO y ADMIN (TECNICO `403`).
    `FilaVista` gana al final `validadaEn` (hora de Madrid), `validadaPor` (nombre de usuario) y `motivoValidado`, todos nullable. Al servir:
    si la fila guarda una validación para su IMEI actual y el aviso calculado sigue siendo de uno de esos motivos de SKU, `nivel` pasa a `OK`,
    `motivo` a `null` (`skuMovil` y `caja` se conservan) y `motivoValidado` es el motivo que tenía el aviso; con otro IMEI, o si el aviso
    ya es otro (p. ej. `EN_OTRA_FILA`), la validación se ignora y los tres campos van `null`. Poner (`PUT .../imei`) un IMEI distinto del
    validado, o quitarlo, borra la validación en la misma transacción y apunta `VALIDACION_QUITADA` (`detalle` `"al cambiar el IMEI"`,
    usuario el que cambió el IMEI). Historial: tipos nuevos `VALIDADA` (`detalle` = el motivo, p. ej. `COLOR_O_GRADO`) y `VALIDACION_QUITADA`
    (`detalle` nulo, o `"al cambiar el IMEI"`); `imeiAnterior` e `imeiNuevo` nulos. La validación no cambia `estado` ni `GET .../vendidos`.
  - `GET /api/pedidos/vendidos`: **ninguna fila con marca se sirve**, tenga o no IMEI. La fecha que cuenta para la ventana de días y es la
    `fechaImei` de la respuesta es la más reciente entre el último apunte de tipo `IMEI` y el último `MARCA_QUITADA` de la fila: al quitar
    la marca la fila vuelve como recién puesta aunque el IMEI sea antiguo. Los apuntes `MARCA`, `FALTA_ENVIAR` y `YA_ENVIADO` no cuentan.
  - `POST /api/pedidos/sincronizados` (no es una ruta de usuario: aviso del plugin de la web) con la cabecera `X-API-Key` y el cuerpo
    `{canal, pedidos: [{pedido, imeis: [..]}]}` → `{filasSincronizadas, marcasQuitadas, sinFila}`. La clave es una de las del ajuste
    `PEDIDOS_SINCRONIZADOS_CLAVE` (distinto del de los vendidos; una clave puede estar en los dos): sin ninguna válida `404` para todos,
    clave ausente o mala `401`, un token de usuario no vale y la clave no abre ninguna otra ruta. `canal`: `DISTRIBUIDORES`, `PARTICULARES`
    o `BACKIPHONE` (otro, `400`); hasta 500 pedidos y 50 `imeis` por pedido, cada IMEI de 15 cifras, `pedido` obligatorio (si no, `400`).
    El número de pedido se normaliza como lo guarda la lectura de ShippyPro (sin espacios en los extremos y cortado al ancho de la columna).
    En una transacción, para cada pedido, las filas de ShippyPro de ese canal y número cuyo IMEI viene en `imeis` reciben
    `sincronizadaEn` = ahora y, si tenían la marca «falta enviar», la pierden con un apunte `YA_ENVIADO` sin usuario (`usuario` nulo: la
    web lo muestra como «plugin de la web»). Las demás filas del pedido (el móvil que aún falta) no se tocan; pedidos e IMEI que no casan
    se ignoran. `filasSincronizadas`: filas puestas al día; `marcasQuitadas`: marcas quitadas; `sinFila`: IMEI enviados (distintos, por
    pedido) que no casaron con ninguna fila. Idempotente: repetir la llamada no crea apuntes nuevos.
  - Asociación de IMEI y pedido en Amazon («plan B»; spec `2026-11-13-pedidos-amazon-asociacion-design.md`): en `AMAZON` y
    `AMAZON_STOCKROOM` el pedido de una fila solo vale cuando lo confirma el libro de ventas. `FilaVista` gana al final `asociadaEn`
    (nullable, hora de Madrid: cuándo se confirmó; `null` = sin asociar) y `asociadaOrigen` (nullable: `LIBRO` o `HISTORICO`). En Amazon
    `pedido` sigue viniendo aunque la fila no esté asociada (lo necesita el estado): la web enseña «sin asociar» y no el número. En las
    webs y el histórico los dos son `null` y no significan nada. El buscador (`buscar`) no encuentra por número de pedido las filas de
    Amazon sin asociar (sí por IMEI, SKU o plataforma). `GET /api/pedidos/vendidos`: en las filas de Amazon sin asociar `pedido` va
    `null` (el IMEI se sirve igual). Historial: tipos nuevos `ASOCIADA` (`detalle` `LIBRO` o `HISTORICO`) e `IMEI_INTERCAMBIADO`
    (`detalle` `"con la fila <id>"`), con `usuario` nulo (lo hace una máquina) y los dos IMEI nulos; cada cambio de IMEI que hace la
    asociación apunta además su `IMEI` como siempre. La carga del histórico da por asociadas (`HISTORICO`) las filas de Amazon de la regla A
    (la que recibe el IMEI por el número de pedido de la hoja y la del mismo pedido que ya lo tenía); B no asocia.
    - `POST /api/pedidos/asociaciones` (no es una ruta de usuario: una pasada del libro de ventas) con la cabecera `X-API-Key` y el
      cuerpo `PeticionAsociaciones` `{canal, pares: [{pedido, imei}]}` → `RespuestaAsociaciones` `{confirmadas, intercambiadas, puestas,
      yaEstaban, paraRevisar, descartadas, ajenos}`. La clave es una de las del ajuste `PEDIDOS_ASOCIACIONES_CLAVE` (propio): sin ninguna válida
      `404` para todos, clave ausente o mala `401` (también las de vendidos o del plugin), un token de usuario no vale y la clave no abre
      ninguna otra ruta (ni el `GET` de la misma). `canal`: `AMAZON` o `AMAZON_STOCKROOM` (otro, `400`); `pares` obligatorio, hasta
      10.000 (si no, `400`). Los pares no dan `400`: el número se normaliza como lo guarda la lectura de ShippyPro y el IMEI debe ser de 15
      cifras tras recortar espacios (un número JSON también vale); lo que no (pedido vacío, «CANCELADO», vacíos, otros números) cuenta en
      `descartadas`, y si un IMEI sale varias veces solo vale la última (las anteriores, `descartadas`). En una transacción y en el orden
      recibido, sobre las filas de ShippyPro de ese canal, cada «IMEI X → pedido B»: (1) X ya está en una fila de B: se confirma
      (`confirmadas`; si ya lo estaba, `yaEstaban` sin tocar nada; también con la etiqueta generada); (2) X está en una fila de otro pedido
      sin etiqueta: se intercambian los IMEI con la primera fila de B sin confirmar ni etiqueta (por posición), X queda confirmado y la otra
      fila recibe el IMEI que tenía la de B (o ninguno) y queda sin confirmar aunque lo estuviera (`intercambiadas`); (3) X no está en
      ninguna fila: va a la primera fila libre de B (sin IMEI, sin confirmar, sin etiqueta y no cancelada) y se confirma (`puestas`); nunca
      saca de la tabla un IMEI que alguien leyó: sin fila libre no se toca nada, aunque B tenga filas sin confirmar con otro IMEI; (4) si B no tiene filas en el ERP (salió antes de que el ERP leyera ShippyPro, o es antiguo) no se toca nada y se cuenta aparte en `ajenos`
      (no va a la lista ni a `paraRevisar`; el motivo `PEDIDO_AJENO` ya no se guarda); si no se puede por otra causa, no se toca nada y queda para revisar (`paraRevisar`) con
      su motivo: `YA_SALIO` (X está en una fila con etiqueta de otro pedido), `CANCELADO` (las
      únicas filas posibles —la de B que tiene X, la de otro pedido que lo tiene o los huecos de B— tienen la marca «Cancelado») o
      `SIN_HUECO` (en (2), B no tiene fila sin confirmar ni etiqueta; en (3), B no tiene fila libre). Nunca cambia el IMEI de una fila con etiqueta (`envioFecha`) ni con la marca
      `CANCELADO`, ni confirma una cancelada; nunca toca el histórico. Poner un IMEI en una fila `ESPERA_STOCK` le quita la marca (apunte
      `MARCA_QUITADA` `"ESPERA_STOCK — al poner el IMEI"`). Al terminar de recorrer los pares se vuelven a intentar los que quedaron `SIN_HUECO` (su hueco puede estar ocupado por un IMEI que un par posterior
      saca de ahí con la regla 2), repitiendo mientras alguno avance y hasta 3 vueltas, en la misma transacción; solo los que siguen sin hueco van a
      `paraRevisar`. Cada pasada rehace la lista «para revisar» de su canal y guarda sus recuentos (`ajenos` solo está en la respuesta del `POST`).
      Idempotente: repetir la misma pasada no cambia nada (todo `yaEstaban`, `paraRevisar` o `ajenos`).
    - `GET /api/bot/devoluciones` (no es una ruta de usuario: el bot de stock de NSYS; cabecera `X-API-Key`) → `ListaDevoluciones`
      `{grupos: [{destino, imeis: [{imei, motivo}]}]}`: los IMEI a devolver, agrupados por la caja de destino; `motivo` es
      `IMEI_CORREGIDO` hoy (la reserva de IMEI añadirá `RESERVA_LIBERADA`). Solo se miran los descuentos de los últimos 90 días
      (`DIAS_DESCONTADO`).
    - `POST /api/bot/movimientos` (misma clave) con el cuerpo `{run, movimientos: [{imei, accion, resultado, origen, destino, motivo,
      fecha}]}` → `{"guardados": n, "repetidos": m}`: lo que el bot movió en una vuelta (`run`). `accion`: `DESCONTADO` o `DEVUELTO`;
      `resultado`: `OK`, `NO_APARECE` o `ERROR`; `fecha`, hora de Madrid del bot (`2026-11-25T10:12:03`). Un movimiento con la misma
      vuelta, IMEI y acción que otro ya guardado cuenta en `repetidos` y no se guarda dos veces. Validación (`400`, sin guardar nada):
      `run` obligatorio (40 caracteres como mucho); `movimientos` presente y hasta 2.000; en cada uno `imei` obligatorio (40 como mucho),
      `accion` y `resultado` de la lista, `fecha` obligatoria, `origen` y `destino` de 80 como mucho, `motivo` de 30 como mucho y `destino`
      obligatorio si la acción es `DEVUELTO`. La clave es una de las del ajuste `PEDIDOS_BOT_CLAVE` (propio): sin ninguna válida `404` para
      todos, clave ausente o mala `401` (también las de vendidos, plugin o asociaciones), un token de usuario no vale y la clave no abre
      ninguna otra ruta.
    - `GET /api/pedidos/asociaciones` (SUPERTECNICO y ADMIN; TECNICO `403`) → `EstadoAsociaciones` `{canales: [{canal, cuando,
      confirmadas, intercambiadas, puestas, yaEstaban, paraRevisar, descartadas, libroModificado, error, errorEn, revisar: [{pedido, imei,
      motivo, vistoEn, detalle}]}]}`: un elemento por canal que ha tenido alguna pasada (`AMAZON` antes que `AMAZON_STOCKROOM`; lista vacía si no
      hubo ninguna), con la última pasada (`cuando`, hora de Madrid) y su lista «para revisar» en el orden del libro (`vistoEn`: la pasada
      que la dejó). Campos añadidos con el lector de los libros (nullable; la web que no los conoce los ignora): `libroModificado`, la
      fecha de modificación en Drive (hora de Madrid, al segundo) del libro de la última lectura buena del lector (`null` si el lector
      nunca lo leyó; las pasadas por `POST` no la cambian); `error` y `errorEn`, el fallo de la última vuelta del lector en ese canal y
      cuándo (`null` si la última fue buena: una lectura buena los borra). `error` es un texto para la píldora, p. ej. «falta la columna
      IMEI en Paso 1. Meter IMEI», «falta la pestaña Paso 1. Meter IMEI» o «no se pudo leer el libro de Drive: …». Si la primera vuelta
      del lector en un canal falla, el canal aparece con `cuando` = la hora del error, los recuentos a 0 y `error`. El resto no es
      nullable.
      Cada elemento de `revisar` es `{pedido, imei, motivo, vistoEn, detalle}`; `detalle` puede ser `null`. Desde la 0.9.46.2 el libro
      manda siempre (pone el IMEI aunque no cuadre, esté en otro pedido o el pedido ya estuviera asociado con otro; la celda avisa) y
      solo quedan para revisar `SIN_HUECO` (no hay fila libre donde poner el IMEI) y `CANCELADO` (el pedido solo tiene filas canceladas).
      `YA_SALIO`, `REPETIDO_EN_LIBRO`, `NO_EN_NSYS` y `OTRO_MODELO` (0.9.46 y 0.9.46.1) ya no se generan; la web los sigue sabiendo leer.
      El libro también corrige el IMEI de pedidos con etiqueta (spec `2026-11-24-asociacion-libro-manda`).
    - Lector de los libros (ajuste `PEDIDOS_LIBROS_ACTIVA`, apagado por defecto; ver `docs/deployment.md`): cada 10 minutos, por canal
      con libro (`PEDIDOS_LIBRO_AMAZON`, `PEDIDOS_LIBRO_STOCKROOM`), si el fichero cambió en Drive desde su última lectura buena, lee la
      pestaña «Paso 1. Meter IMEI» (cabecera en la fila 6; columnas «order id», «purchase-date» e «IMEI» por su nombre, sin mayúsculas ni
      espacios de los extremos) y aplica, como una pasada del `POST`, los pares de las filas con IMEI y «purchase-date» de los últimos 30
      días (fecha de Excel o texto `2026-11-13T10:22:33+00:00` —el día en Madrid—, `2026-11-13` o `13/11/2026`). Un IMEI numérico se
      toma de su valor guardado, con sus 15 cifras exactas; uno en texto, sin espacios. No es una ruta: no cambia el contrato salvo
      los tres campos de arriba.
  - `GET /api/pedidos/fuentes` → `[FuenteVista]`, siempre dos y en este orden: `SHIPPYPRO` y `NSYS` (`ultimaBuena`, `resultado`,
    `filas`, `skuDesconocidos`, `motivo`, `nivel` de datos antiguos: `OK`, `RETRASO` o `SIN_DATOS`, y `aviso`). Un `ERROR` enseña un motivo
    fijo, nunca el texto de la excepción. `aviso` (nullable, campo añadido; la web que no lo conoce lo ignora) lleva un texto fijo en
    lenguaje de usuario, sin números de pedido ni datos, cuando la última lectura fue `OK` pero se saltó el cierre y el marcado de envíos:
    hay pedidos de ShippyPro sin fecha válida (no se cierra ninguna fila ni se marca ningún envío hasta que se resuelvan) o no se pudieron
    leer los envíos (por ahora no se marcan envíos ni etiquetas). Es `null` en `NSYS`, con `ERROR` o `RECHAZADA` (habla `motivo`) y en
    una lectura normal; la web puede enseñar una advertencia en la píldora en vez de un `OK` liso.
  - Carga del histórico de la hoja (solo **ADMIN**; SUPERTECNICO y TECNICO reciben `403`), una herramienta a mano que nunca se
    enciende sola y se puede repetir: `POST /api/pedidos/historico` (sin cuerpo) arranca la carga en segundo plano y responde `202` con
    `CargaHistoricoEstadoCarga` (`estado` `EN_CURSO`); `409` si ya hay una en marcha; `400` "No está configurada la hoja del
    histórico (pedidos.historico.hoja)" si falta el ajuste. `GET /api/pedidos/historico` → `CargaHistoricoEstadoCarga`
    `{estado, empezo, acabo, informe, motivo}`, el estado de la última carga de este arranque (solo en memoria): `estado` es
    `NUNCA`, `EN_CURSO`, `OK` (con `informe`) o `ERROR` (con `motivo`, un texto fijo en lenguaje de usuario: nunca el texto de la
    excepción ni el id de la hoja); `empezo`, `acabo`, `informe` y `motivo` son `null` cuando no corresponden (`informe` va como
    `$ref`, así que el contrato no lo marca `nullable`). `InformeCarga` solo lleva recuentos (ningún IMEI, pedido ni fila): `filasLeidas`,
    `cargadasHistorico`, `descartadasVacias`, `sinFechaHeredada`, `sinFechaDescartada`, `incidenciasDescartadas` (filas cuya Plataforma, sin espacios ni mayúsculas, empieza por `INCIDENCIA`: no se cargan ni cuentan para nada), `pendientesSinImeiNoCargadas` (filas de los últimos 14 días, contados desde el día de la carga y con el día 14 incluido, sin IMEI de 15 cifras —vacío o con un texto que no es IMEI— y sin número de pedido: son pedidos pendientes que el ERP ya tiene por ShippyPro, no se cargan ni entran en los traspasos; las de más de 14 días sí se cargan), `imeiNoEsImei`, `traspasadasA`, `traspasadasB`,
    `traspasadasC`, `yaPuesto`, `conflicto`, `casadasSinImei`, `sinPareja`, `yaVendidoNsys` (candidatas de B o C cuyo IMEI NSYS da por vendido: van al histórico), `ventaAnterior` (su IMEI ya está en una fila de ShippyPro pero la fila de la hoja es de más de 7 días antes que esa fila: otra venta del mismo móvil, se carga como histórico) y `plataformas` (objeto nombre → filas con las 15
    plataformas con más filas, sin las de incidencia). Cuadre: `filasLeidas` = `descartadasVacias` + `sinFechaDescartada` + `incidenciasDescartadas` + `pendientesSinImeiNoCargadas` + `cargadasHistorico` + `traspasadasA` + `traspasadasB` + `traspasadasC` + `yaPuesto` + `conflicto` + `casadasSinImei`. La carga lee la hoja entera por columnas de cabecera (`Fecha`, `Plataforma`, `SKU`, `IMEI`, y si están
    `Pedido Amazon` y `Fecha compra`) y, en una sola transacción, borra las filas `HISTORICO`, pone en las filas de ShippyPro los IMEI
    de la hoja que son de un pedido que el ERP ya tiene (A: por número de pedido; B: AMZ/FNSK/FSNK sin número, de los últimos 14
    días; C: webs, de los últimos 14 días), en dos pasadas (A con toda la hoja y después B y C, que no usan filas cuyo número de pedido aparece en la hoja ni IMEI que NSYS da por vendidos; las filas de la hoja, de la más reciente a la más antigua, toman la primera pendiente libre (por fecha de compra) cuya `FECHA` no sea más de 7 días posterior a la de la fila de la hoja (así repetir la carga da lo mismo); en A, un IMEI que está en otra fila del ERP de más de 7 días antes que la fila de la hoja es una venta anterior del móvil (revendido) y se traspasa igualmente; las que no encuentran pendiente se cargan como histórico, «sin pareja»), sin poner nunca un IMEI que ya esté en otra fila de ShippyPro (`yaPuesto` si la fila de la hoja no es de más de 7 días antes que la fila del ERP). Si la hoja trae menos de `pedidos.historico.minimo` filas útiles, sin contar las de incidencia (por defecto 1000) la carga acaba en `ERROR` sin tocar nada y
    crea el resto como filas `HISTORICO` cerradas, en el orden de la hoja. Ajustes: `pedidos.historico.hoja` y
    `pedidos.historico.pestana` (por defecto `PEDIDOS 2026`) y `pedidos.historico.minimo` (por defecto 1000).
- `GET /api/reparaciones/pendientes/contadores` → `ContadoresPendientes` `{reparaciones, glass, pulidos}`:
  asignaciones abiertas (`A`, `AG`, `AP` sin `FECHA_FIN`) del técnico efectivo. Sin `?tecnico=` cuenta las
  del técnico del token (también el SUPERTECNICO); un ADMIN sin técnico recibe ceros.
- Escrituras del formulario de reparación (`POST /api/reparaciones/completa`, `POST /api/reparaciones/{idAsignacion}/filas`,
  `POST /api/reparaciones/{idAsignacion}/agotar-componente` y `GET|PUT|DELETE /api/reparaciones/{idRep}/borrador`): pasan por `PropiedadAsignacion`. El técnico es el del token y la
  asignación debe ser suya (`403` "Solo puedes trabajar sobre tus propias asignaciones"); el `idTec` del cuerpo sigue en
  el contrato por compatibilidad y el servidor lo ignora cuando hay `idAsignacion`. `completa` sin `idAsignacion` (filas
  añadidas al editar una reparación ya hecha) exige SUPERTECNICO y conserva el `idTec` enviado, que es el del técnico
  original. `PUT /api/reparaciones/{idRep}` y `GET /api/reparaciones/{idRep}/detalle-edicion`: SUPERTECNICO. Detalle en
  `docs/autorizacion_endpoints.md`.
- En `/api/reparaciones/{idRep}/borrador` la variable de ruta es el id de la **asignación**. El contenido es un JSON opaco
  para el servidor (lo escriben y lo leen los clientes con el mismo formato); `GET` responde `ContenidoBorrador`
  `{"contenido": "<json>" | null}`.
- Campos sin valor en los cuerpos: enviar la propiedad a `null` equivale a omitirla. El contrato marca todas las
  propiedades como `required` y señala con `nullable` las que admiten `null`.
- Envoltorios de un solo valor: `ValorTexto` `{"value": texto | null}` (`…/referenciadora`, `…/incidencia-activa`,
  `POST …/filas` con el id de la reparación creada, `GET /api/telefonos/{imei}/modelo`, que responde `""` si no hay
  modelo), `ValorEntero` `{"value": n}` (`GET /api/solicitudes/count`, `GET /api/solicitudes-stock/count`) y
  `ValorBooleano` `{"value": true | false}`.
- `PATCH /api/solicitudes/{idRc}/estado` y `PATCH /api/solicitudes-stock/{idSol}/estado` reciben `{"estado": "PENDIENTE" |
  "GESTIONADA" | "RECHAZADA"}`. `/api/solicitudes` es de SUPERTECNICO; en `/api/solicitudes-stock` crean TECNICO y
  SUPERTECNICO, leen y cuentan SUPERTECNICO y ADMIN, y cambian de estado o borran SUPERTECNICO.
- Reintentos seguros: las cuatro escrituras no repetibles del formulario (`POST /api/reparaciones/completa`,
  `POST /api/reparaciones/{idAsignacion}/filas`, `POST /api/reparaciones/{idAsignacion}/agotar-componente`,
  `PUT /api/reparaciones/{idRep}`) aceptan la cabecera opcional `Idempotency-Key`. Con la misma clave, el mismo
  usuario y la misma petición (ruta y cuerpo), el servidor devuelve el resultado de la primera ejecución sin
  repetirla, incluido el log de actividad; con la misma clave y otra petición responde `422`; mientras la primera
  ejecución sigue en curso, un reintento responde `409`. El registro vive en la memoria del proceso, caduca a
  las 24 horas y tiene tope de tamaño (se purgan las entradas caducadas y, si hace falta, la más antigua). Sin
  cabecera el comportamiento es exactamente el de siempre, así que el cliente de escritorio no se entera.
- Chasis por SKU: al completar con una pieza cuyo SKU empieza por `cha`, o al pedirla (agotado o solicitud dentro de
  `completa`), la asignación queda con `esChasis = true`. El servidor nunca lo quita por sí solo; el cambio manual
  (`PATCH /api/reparaciones/asignaciones/{idRep}/chasis`) sigue igual.
- Bloques de pedidos (0.9.9): `GET /api/compras` trae en cada línea su bloque (`idBloque`, `fechaBloque`, `notaBloque`,
  `bloqueUpdatedAt`, `bloqueAntiguo`). `POST /api/bloques-compra/{idBloque}/confirmar|recibir|cancelar|borrar|separar` y
  `…/juntar` reciben la lista exacta de líneas que vio el cliente (`lineas: [{idCompra, updatedAt}]`) y son todo o nada:
  si una no existe, no es del bloque o cambió (y, en confirmar/recibir/cancelar/borrar, si no está en el estado de la
  acción), `409` "Este bloque fue modificado por
  otro usuario." sin cambiar nada; `borrar` y `juntar` exigen el bloque entero; `separar` devuelve `{idBloque}` (el bloque nuevo) y no puede llevarse todas
  las líneas (`422`). Un bloque que se queda sin líneas desaparece en la misma operación; si una escritura concurrente
  lo deja vivo (borrar/juntar) o vacío (separar), `409` y se revierte. `PATCH /api/bloques-compra/{idBloque}/nota`
  (`{nota, updatedAt}`): `422` si pasa de 200 caracteres, `409` si el bloque cambió. `PATCH /api/compras/{idCompra}/bloque`
  (`{idBloque, updatedAt}`, `idBloque` nulo = bloque nuevo): `404` si la línea no existe, `409` si cambió (versión o
  bloque) o si el bloque de destino ya no existe («El bloque N ya no existe; elige otro destino.»), `422` si ya está
  en ese bloque («La línea ya está en ese bloque.»), si ya está sola en su bloque y el destino es uno nuevo («La línea
  ya está sola en su bloque.») o si el destino es de otro proveedor («El bloque N es de otro proveedor.»). `422` «El bloque N ya está terminado.» si el destino (lote, mover, editar, juntar) o el origen (juntar, separar) no tiene ninguna línea pendiente, en camino ni parcial, y `422` «Una línea recibida o cancelada no se puede mover.» al mover o separar una línea en esos estados (spec 2026-10-12). `POST /api/compras/lote` acepta `destinos: [{idProv, idBloque}]` (`idBloque` nulo =
  bloque nuevo; un proveedor sin entrada va a su bloque abierto más reciente o a uno nuevo) y `PUT /api/compras/{id}`
  acepta `destino: {idBloque}` cuando cambia el proveedor. Solo SUPERTECNICO.
- Otros pedidos en bloques (0.9.15): un otro (`Compra_otro`) es una línea más del bloque de su proveedor.
  `GET /api/compras-otros` trae en cada otro su bloque (los cinco campos de `GET /api/compras`). En las acciones de
  bloque cada línea lleva `tipo` (`PIEZA` u `OTRO`; nulo o ausente = `PIEZA`) e `idCompra` es el id en su tabla
  (`ID_COMPRA` o `ID_COMPRA_OTRO`): las reglas y los `409` son los de siempre, comparando por tipo + id; recibir un otro
  no toca el stock. `POST /api/compras/lote` acepta `otros: [{idProv, concepto, cantidad, esUrgente, precioUnidad}]`
  (nulo = ninguno; `lineas` puede ir vacía si hay otros): van al mismo bloque que las piezas de su proveedor, se
  validan después de las piezas con `422` «Otro n: …» y la respuesta añade `idsOtrosCreados`.
  `POST /api/compras-otros/lote` y `POST /api/compras-otros` meten cada otro en el bloque abierto más reciente de su
  proveedor o en uno nuevo. Un concepto que, recortado, pasa de 255 caracteres es `422` («El concepto no puede pasar de
  255 caracteres.»; en los lotes, «Línea n: …» u «Otro n: …»); se guarda recortado.
  `PATCH /api/compras-otros/{id}/bloque` (`{idBloque, updatedAt}`) tiene las reglas y los mensajes del de piezas;
  `PUT /api/compras-otros/{id}` acepta `destino: {idBloque}` cuando cambia el proveedor y `DELETE /api/compras-otros/{id}`
  borra también el bloque si se queda vacío. Solo SUPERTECNICO.
- Tipos de pieza de cada proveedor (0.9.9, spec 2026-10-12): `GET /api/proveedores/{idProv}/piezas` (SUPERTECNICO y
  ADMIN) devuelve todos los tipos que existen (prefijo de las referencias no «otro», activas o no; orden bat, cha,
  tapa, g, mc, lcd y el resto alfabético) como `[{prefijo, vende, modelos, excluidos}]`: `modelos` = los de sus piezas
  activas más los excluidos, en el orden de la tienda. `PUT /api/proveedores/{idProv}/piezas` (solo SUPERTECNICO) con
  `{ "tipos": [{ "prefijo": "bat", "excluidos": ["12mini"] }] }` sustituye todo (sin excepciones = todos los modelos,
  también los futuros; lista vacía = no vende nada) y responde `204`. `404` «Proveedor no encontrado.»; `422` «Solo los
  proveedores de componentes tienen piezas.», «Falta la lista de tipos.», «Falta la lista de modelos excluidos.»,
  «Algún tipo de pieza no existe.», «Un tipo de pieza está repetido.», «Algún modelo no existe.» o «Si no vende ningún
  modelo de un tipo, desmarca el tipo.» (sin cambiar nada). Registra `EDITAR_PIEZAS_PROVEEDOR` con
  `ID_PROV: <id>, PIEZAS: bat (todos), g (sin 12mini, 13mini)` («ninguna» sin tipos). En `Componente`
  (`GET /api/componentes/gestionados`), `pendiente` (unidades en pedidos pendientes) y `proveedores` (ids de los
  proveedores que venden alguna pieza de su grupo) son nulos para TECNICO.
- Sin sesión: una petición sin cabecera `Authorization` recibe `403` (Spring Security sin entry point);
  con token inválido o caducado, `401` (filtro JWT). Los clientes tratan ambos como "sin sesión" cuando
  no hay token, y `401` como sesión caducada.
- Regenerar el snapshot: `mvn test -Dtest='OpenApiContractTest'` deja el contrato en
  `target/openapi.json`; la web lo copia a `api/openapi.json` y ejecuta `npm run api:types:offline`.
