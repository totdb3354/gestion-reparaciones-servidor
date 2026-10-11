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
- `GET /api/reparaciones/pendientes/contadores` → `ContadoresPendientes` `{reparaciones, glass, pulidos}`:
  asignaciones abiertas (`A`, `AG`, `AP` sin `FECHA_FIN`) del técnico efectivo. Sin `?tecnico=` cuenta las
  del técnico del token (también el SUPERTECNICO); un ADMIN sin técnico recibe ceros.
- Escrituras del formulario de reparación (`POST /api/reparaciones/completa`, `POST /api/reparaciones/{idAsignacion}/filas`,
  `POST /api/reparaciones/{idAsignacion}/agotar-componente`, `PATCH /api/reparaciones/{idRep}/completar` y
  `GET|PUT|DELETE /api/reparaciones/{idRep}/borrador`): pasan por `PropiedadAsignacion`. El técnico es el del token y la
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
  `PATCH /api/componentes/{idCom}/stock`: SUPERTECNICO.
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
  ya está sola en su bloque.») o si el destino es de otro proveedor («El bloque N es de otro proveedor.»). `POST /api/compras/lote` acepta `destinos: [{idProv, idBloque}]` (`idBloque` nulo =
  bloque nuevo; un proveedor sin entrada va a su bloque abierto más reciente o a uno nuevo) y `PUT /api/compras/{id}`
  acepta `destino: {idBloque}` cuando cambia el proveedor. Solo SUPERTECNICO.
- Sin sesión: una petición sin cabecera `Authorization` recibe `403` (Spring Security sin entry point);
  con token inválido o caducado, `401` (filtro JWT). Los clientes tratan ambos como "sin sesión" cuando
  no hay token, y `401` como sesión caducada.
- Regenerar el snapshot: `mvn test -Dtest='OpenApiContractTest'` deja el contrato en
  `target/openapi.json`; la web lo copia a `api/openapi.json` y ejecuta `npm run api:types:offline`.
