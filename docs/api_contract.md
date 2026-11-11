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
  anteriores (no se mira `Pedido_fila.FECHA`); como mucho 5000. `dias`: entero de 1 a 31 (por defecto 7); fuera de rango o no
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
    `AMAZON_STOCKROOM`) y es `null` en todo lo demás (clientes, incidencias, `NP`, vacía).
  - `GET /api/pedidos/filas/{id}/cambios` → lista de `CambioImei` `{usuario, fecha, imeiAnterior, imeiNuevo}` con los apuntes de
    IMEI de la fila, del más reciente al más antiguo (hasta 200; `fecha` y hora de Madrid sin zona). `usuario` (nombre de usuario),
    `imeiAnterior` e `imeiNuevo` son nullable (`usuario` nulo en el traspaso desde la hoja; `imeiNuevo` nulo = se quitó). `404` si la
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
  - `PUT /api/pedidos/filas/{id}/imei` con `PeticionImei` `{imei}` → `FilaVista` releída, con su aviso y su `canal`. `imei` nulo o en blanco
    lo quita; si no, 15 cifras (`400` "Un IMEI son 15 cifras"). `404` si la fila no existe; `409` "La fila es del histórico y no se edita" si es del
    histórico (las filas de ShippyPro se editan siempre, también las enviadas). Cabecera opcional `Idempotency-Key`; el cambio queda apuntado con el usuario del token.
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
    `cargadasHistorico`, `descartadasVacias`, `sinFechaHeredada`, `sinFechaDescartada`, `imeiNoEsImei`, `traspasadasA`, `traspasadasB`,
    `traspasadasC`, `yaPuesto`, `conflicto`, `casadasSinImei`, `sinPareja`, `yaVendidoNsys` (candidatas de B o C cuyo IMEI NSYS da por vendido: van al histórico), `ventaAnterior` (su IMEI ya está en una fila de ShippyPro pero la fila de la hoja es de más de 7 días antes que esa fila: otra venta del mismo móvil, se carga como histórico) y `plataformas` (objeto nombre → filas con las 15
    plataformas con más filas). La carga lee la hoja entera por columnas de cabecera (`Fecha`, `Plataforma`, `SKU`, `IMEI`, y si están
    `Pedido Amazon` y `Fecha compra`) y, en una sola transacción, borra las filas `HISTORICO`, pone en las filas de ShippyPro los IMEI
    de la hoja que son de un pedido que el ERP ya tiene (A: por número de pedido; B: AMZ/FNSK/FSNK sin número, de los últimos 14
    días; C: webs, de los últimos 14 días), en dos pasadas (A con toda la hoja y después B y C, que no usan filas cuyo número de pedido aparece en la hoja ni IMEI que NSYS da por vendidos; las filas de la hoja, de la más reciente a la más antigua, toman la primera pendiente libre (por fecha de compra) cuya `FECHA` no sea más de 7 días posterior a la de la fila de la hoja (así repetir la carga da lo mismo); en A, un IMEI que está en otra fila del ERP de más de 7 días antes que la fila de la hoja es una venta anterior del móvil (revendido) y se traspasa igualmente; las que no encuentran pendiente se cargan como histórico, «sin pareja»), sin poner nunca un IMEI que ya esté en otra fila de ShippyPro (`yaPuesto` si la fila de la hoja no es de más de 7 días antes que la fila del ERP). Si la hoja trae menos de `pedidos.historico.minimo` filas útiles (por defecto 1000) la carga acaba en `ERROR` sin tocar nada y
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
