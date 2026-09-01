# API Contract — Gestión Reparaciones

**Base URL (desarrollo):** `http://localhost:8080`  
**Base URL (producción):** `https://<ip-vm>`  
**Content-Type:** `application/json`

---

## Autenticación

Todos los endpoints excepto `/api/auth/login` requieren el header:

```
Authorization: Bearer <token>
```

El token se obtiene del login y tiene una validez de 24 horas.

---

## `/api/auth`

### POST `/api/auth/login`
Sin autenticación requerida.

**Request:**
```json
{ "usuario": "admin", "password": "admin123" }
```

**Response 200:**
```json
{
  "idUsu": 1,
  "nombreUsuario": "admin",
  "rol": "ADMIN",
  "idTec": null,
  "token": "eyJhbGci..."
}
```

**Response 401:** credenciales incorrectas.

---

## `/api/tecnicos`

### GET `/api/tecnicos`
Devuelve todos los técnicos (activos e inactivos).  
**Response:** `[{ "idTec": 1, "nombre": "Juan", "activo": true }, ...]`

### GET `/api/tecnicos/activos`
Solo técnicos con `ACTIVO = true`.

### POST `/api/tecnicos`
**Request:** `{ "nombre": "Juan García" }`  
**Response:** 201 Created

### DELETE `/api/tecnicos/{idTec}`
**Response:** 204 No Content

---

## `/api/usuarios`
> Todos los endpoints requieren rol `ADMIN`.

### GET `/api/usuarios/tecnicos`
Devuelve usuarios con rol TECNICO incluyendo datos del técnico asociado.  
**Response:** `[{ "idUsu": 2, "nombreUsuario": "juan", "rol": "TECNICO", "idTec": 1, "nombreTecnico": "Juan García", "activo": true }, ...]`

### POST `/api/usuarios/tecnicos`
Crea técnico + usuario en una transacción.  
**Request:**
```json
{ "nombreTecnico": "Juan García", "nombreUsuario": "juan", "password": "pass123" }
```
**Response:** 201 Created | 409 Conflict (nombreUsuario ya existe)

### PATCH `/api/usuarios/tecnicos/{idTec}/activar`
**Response:** 204 No Content

### PATCH `/api/usuarios/tecnicos/{idTec}/desactivar`
**Response:** 204 No Content

### GET `/api/usuarios/tecnicos/{idTec}/tiene-reparaciones`
**Response:** `{ "value": true }`

### DELETE `/api/usuarios/tecnicos/{idTec}?idUsu={idUsu}`
Elimina técnico y su usuario. Requiere parámetro `idUsu`.  
**Response:** 204 No Content

---

## `/api/proveedores`

### GET `/api/proveedores`
Todos los proveedores.  
**Response:** `[{ "idProv": 1, "nombre": "Proveedor SA", "activo": true, "divisa": "EUR" }, ...]`

### GET `/api/proveedores/activos`
Solo proveedores activos.

### GET `/api/proveedores/{idProv}/tiene-pedidos`
**Response:** `{ "value": false }`

### POST `/api/proveedores`
**Request:** `{ "nombre": "Proveedor SA" }`  
**Response:** 201 Created

### PATCH `/api/proveedores/{idProv}/activo`
**Request:** `{ "activo": false }`  
**Response:** 200 OK

### PATCH `/api/proveedores/{idProv}/divisa`
**Request:** `{ "divisa": "USD" }`  
**Response:** 200 OK

### DELETE `/api/proveedores/{idProv}`
**Response:** 204 No Content

---

## `/api/telefonos`

### GET `/api/telefonos`
**Response:** `[{ "imei": "123456789012345" }, ...]`

### GET `/api/telefonos/{imei}/exists`
**Response:** `{ "value": true }`

### POST `/api/telefonos`
**Request:** `{ "imei": "123456789012345" }`  
**Response:** 201 Created

### DELETE `/api/telefonos/{imei}`
**Response:** 204 No Content

---

## `/api/tipo-cambio`

### GET `/api/tipo-cambio/{divisa}`
Devuelve la tasa de cambio más reciente para la divisa.  
**Ejemplo:** `GET /api/tipo-cambio/USD`  
**Response:** `{ "value": 1.085 }`

---

## `/api/componentes`

### GET `/api/componentes`
Todos los componentes.  
**Response:** `[{ "idCom": 1, "tipo": "batN20", "stock": 5, "stockMinimo": 2, "activo": true, "fechaRegistro": "...", "updatedAt": "..." }, ...]`

### GET `/api/componentes/gestionados`
Solo componentes activos (para selección en formularios).

### GET `/api/componentes/stock-bajo`
Componentes con `stock < stockMinimo`.

### GET `/api/componentes/agrupados`
Agrupados por prefijo SKU.  
**Response:** `{ "bat": [...], "lcd": [...], "cha": [...] }`

### GET `/api/componentes/chasis?color={color}`
Carcasas filtradas por color (búsqueda en el tipo).

### GET `/api/componentes/evolucion-stock?granularidad={dia|semana|mes}&desde={YYYY-MM-DD}&hasta={YYYY-MM-DD}`
Evolución histórica del stock total.  
**Response:** `[{ "fecha": "2025-01-01", "stock": 42 }, ...]`

### POST `/api/componentes`
**Request:** `{ "tipo": "batN20", "stock": 10, "stockMinimo": 3 }`  
**Response:** 201 Created

### PUT `/api/componentes/{idCom}`
**Request:** `{ "tipo": "batN20", "stock": 10, "stockMinimo": 3, "updatedAt": "2025-01-01T10:00:00" }`  
**Response:** 200 OK | 409 Conflict (optimistic lock — el registro fue modificado por otro cliente)

### PATCH `/api/componentes/{idCom}/stock-minimo`
**Request:** `{ "stockMinimo": 5 }`

### PATCH `/api/componentes/{idCom}/stock`
Suma o resta al stock actual.  
**Request:** `{ "delta": -1 }` (negativo = bajada de stock)

### PATCH `/api/componentes/{idCom}/activo`
**Request:** `{ "activo": false }`

### DELETE `/api/componentes/{idCom}`
**Response:** 204 No Content

---

## `/api/compras`

### GET `/api/compras`
Todos los pedidos.  
**Response:** `[{ "idCompra": 1, "idCom": 3, "idProv": 1, "cantidad": 10, "cantidadRecibida": null, "esUrgente": false, "fechaPedido": "...", "fechaLlegada": null, "precioUnidadPedido": 12.50, "divisa": "EUR", "precioEur": 12.50, "estado": "pendiente", "updatedAt": "..." }, ...]`

### GET `/api/compras/pendientes`
Solo pedidos con `estado = 'pendiente'`.

### GET `/api/compras/cantidad-pendiente/{idCom}`
Cantidad total aún pendiente de recibir para un componente.  
**Response:** `{ "value": 8 }`

### POST `/api/compras`
**Request:**
```json
{
  "idCom": 3, "idProv": 1, "cantidad": 10,
  "esUrgente": false, "precioUnidad": 12.50,
  "divisa": "EUR", "precioEur": 12.50
}
```
**Response:** 201 Created

### PUT `/api/compras/{idCompra}`
**Request:**
```json
{
  "idProv": 1, "cantidad": 10, "esUrgente": false,
  "precioUnidad": 12.50, "divisa": "EUR", "precioEur": 12.50,
  "updatedAt": "2025-01-01T10:00:00"
}
```

### PATCH `/api/compras/{idCompra}/confirmar-recibido`
Marca todo como recibido. Incrementa stock.  
**Request:** `{ "updatedAt": "2025-01-01T10:00:00" }`

### PATCH `/api/compras/{idCompra}/confirmar-parcial`
Recibe parte del pedido.  
**Request:** `{ "cantidadRecibida": 6, "updatedAt": "2025-01-01T10:00:00" }`

### PATCH `/api/compras/{idCompra}/recibir-resto`
Recibe el resto de un pedido parcial.  
**Request:** `{ "cantidadExtra": 4, "updatedAt": "2025-01-01T10:00:00" }`

### PATCH `/api/compras/{idCompra}/confirmar-alterado`
Confirma que el pedido llegó con cantidad diferente a la pedida.  
**Request:** `{ "updatedAt": "2025-01-01T10:00:00" }`

### PATCH `/api/compras/{idCompra}/cancelar`
**Request:** `{ "updatedAt": "2025-01-01T10:00:00" }`

---

## `/api/reparaciones`

### GET `/api/reparaciones`
Todas las reparaciones (tabla raw).

### GET `/api/reparaciones/imei/{imei}`
Reparaciones de un IMEI concreto.

### GET `/api/reparaciones/imei/{imei}/count`
**Response:** `{ "value": 3 }`

### GET `/api/reparaciones/historial?tecnico={idTec}`
Reparaciones completadas. Parámetro `tecnico` opcional — si se omite devuelve todas.  
**Response:** lista de `ReparacionResumen` (incluye nombre técnico, componentes, etc.)

### GET `/api/reparaciones/historial/imei/{imei}`
Historial completo de un IMEI.

### GET `/api/reparaciones/asignaciones?tecnico={idTec}`
Asignaciones abiertas (sin `FECHA_FIN`). Parámetro `tecnico` opcional.

### GET `/api/reparaciones/asignaciones/{idRep}`
Asignación específica por ID.  
**Response 404** si no existe.

### GET `/api/reparaciones/asignaciones/{idAsignacion}/solicitudes`
Solicitudes de piezas de una asignación.

### GET `/api/reparaciones/{idRep}/detalle-edicion`
Datos necesarios para el formulario de edición de una reparación.

### GET `/api/reparaciones/{idRep}/referenciadora`
ID de la reparación que referencia a ésta (si la hay).  
**Response:** `{ "value": "R20250101_2" }` o `{ "value": null }`

### GET `/api/reparaciones/imei/{imei}/ya-reparados?excluir={idRep}`
IDs de componentes que ya se usaron en reparaciones previas del IMEI (excepto la reparación excluida).  
**Response:** `[3, 7, 12]`

### GET `/api/reparaciones/imei/{imei}/incidencia-activa`
ID de la reparación con incidencia abierta para ese IMEI, si existe.  
**Response:** `{ "value": "R20250101_1" }` o `{ "value": null }`

### GET `/api/reparaciones/imei/{imei}/tiene-asignacion?tecnico={idTec}`
Comprueba si el técnico tiene ya una asignación abierta para ese IMEI.  
**Response:** `{ "value": false }`

### GET `/api/reparaciones/estadisticas?granularidad={dia|semana|mes}&desde={YYYY-MM-DD}&hasta={YYYY-MM-DD}`
Estadísticas de reparaciones por técnico en el rango.  
**Response:** `[{ "etiqueta": "2025-01", "tecnico": "Juan", "valor": 12 }, ...]`

### GET `/api/reparaciones/estadisticas/puntos?granularidad={dia|semana|mes}&desde={YYYY-MM-DD}&hasta={YYYY-MM-DD}`
Estadísticas por puntos de dificultad por técnico en el rango (spec 2026-09-01).  
**Response:**
```json
[
  {
    "nombreTecnico": "Juan",
    "periodo": "2025-01",
    "puntos": 12.5,
    "puntosNormales": 9.0,
    "puntosGlass": 2.5,
    "puntosPulidos": 1.0,
    "nNormales": 8,
    "nGlass": 5,
    "nPulidos": 4,
    "nSinPiezas": 2
  }
]
```

### POST `/api/reparaciones`
Inserta una reparación básica.  
**Request:**
```json
{ "imei": "123456789012345", "idTec": 1, "fechaAsig": null, "fechaFin": null }
```
**Response 201:** `{ "value": "R20250427_1" }`

### POST `/api/reparaciones/asignaciones`
Crea una asignación abierta (sin componentes).  
**Request:** `{ "imei": "123456789012345", "idTec": 1 }`  
**Response 201:** `{ "value": "A20250427_1" }`

### POST `/api/reparaciones/completa`
Operación completa: crea reparación + componentes en una transacción.  
**Request:**
```json
{
  "filas": [
    { "idCom": 3, "esReutilizado": false, "esIncidencia": false, "cantidad": 1, "observaciones": "..." }
  ],
  "imei": "123456789012345",
  "idTec": 1,
  "idRepAnterior": null,
  "idAsignacion": "A20250427_1"
}
```
**Response:** 201 Created

### PATCH `/api/reparaciones/{idRep}/completar`
Cierra la reparación (pone `FECHA_FIN = NOW()`).  
**Response:** 200 OK

### PATCH `/api/reparaciones/asignaciones/{idRep}/tecnico`
Reasigna técnico a una asignación.  
**Request:** `{ "idTec": 2, "updatedAt": "2025-01-01T10:00:00" }`

### PUT `/api/reparaciones/{idRep}`
Edita los componentes de una reparación.  
**Request:**
```json
{
  "idComNuevo": 5, "esReutilizadoNuevo": false,
  "observacionNueva": "texto", "piezaViejaRota": false, "nNuevas": 1
}
```

### POST `/api/reparaciones/{idRep}/incidencia`
Marca incidencia en una reparación y crea nueva asignación.  
**Request:** `{ "comentario": "Pantalla rota", "imei": "123456789012345", "idTec": 1 }`  
**Response:** 201 Created

### DELETE `/api/reparaciones/imei/{imei}/incidencia-activa`
Elimina la incidencia activa del IMEI.  
**Response:** 204 No Content

### DELETE `/api/reparaciones/asignaciones/{idAsig}`
Elimina una asignación.  
**Response:** 204 No Content

### DELETE `/api/reparaciones/{idRep}`
Elimina una reparación.  
**Response:** 204 No Content

---

## `/api/reparacion-componentes`

### GET `/api/reparacion-componentes/{idRep}`
Componentes de una reparación.  
**Response:** `[{ "idRc": 1, "idRep": "R20250427_1", "idCom": 3, "esReutilizado": false, "esIncidencia": false, "esResuelto": false, "incidencia": null, "observaciones": "...", "esSolicitud": false, "descripcionSolicitud": null, "estadoSolicitud": "PENDIENTE", "cantidad": 1, "updatedAt": "..." }, ...]`

### POST `/api/reparacion-componentes`
**Request:**
```json
{
  "idRep": "R20250427_1", "idCom": 3,
  "esReutilizado": false, "esIncidencia": false, "esResuelto": false,
  "incidencia": null, "observaciones": "texto",
  "esSolicitud": false, "descripcionSolicitud": null, "cantidad": 1
}
```
**Response:** 201 Created

### DELETE `/api/reparacion-componentes/{idRep}/{idCom}`
**Response:** 204 No Content

### PATCH `/api/reparacion-componentes/{idRep}/incidencia`
**Request:** `{ "comentario": "Texto de la incidencia" }`

### DELETE `/api/reparacion-componentes/{idRep}/incidencia`
Borra la incidencia de todos los componentes de la reparación.  
**Response:** 204 No Content

---

## `/api/valores-dificultad`

### GET `/api/valores-dificultad`
Valores de dificultad (en puntos) usados por las estadísticas por puntos.  
**Response:** `[{ "clave": "bateria", "puntos": 1.0 }, ...]`

### PUT `/api/valores-dificultad`
> Requiere rol `ADMIN`.

Actualiza uno o varios valores. Valida todo antes de escribir nada; solo escribe (y loguea) las claves cuyo valor realmente cambia.  
**Request:**
```json
[{ "clave": "pulido", "puntos": 0.75 }]
```
**Response:** 200 OK  
**Response 422:** clave desconocida, o `puntos` fuera de rango (`< 0` o `> 99.99`).

---

## `/api/solicitudes`

### GET `/api/solicitudes/count`
Número de solicitudes pendientes.  
**Response:** `{ "value": 3 }`

### GET `/api/solicitudes?estado={PENDIENTE|GESTIONADA|RECHAZADA}`
Solicitudes filtradas por estado. Sin parámetro devuelve todas.  
**Response:** lista de `SolicitudResumen` con datos del técnico, reparación y descripción.

### PATCH `/api/solicitudes/{idRc}/estado`
Cambia el estado de una solicitud.  
**Request:** `{ "estado": "GESTIONADA" }`

### PATCH `/api/solicitudes/{idRc}/limpiar`
Desmarca `ES_SOLICITUD` (archiva la solicitud).  
**Response:** 200 OK

---

## Códigos de respuesta comunes

| Código | Significado |
|--------|-------------|
| 200    | OK |
| 201    | Creado |
| 204    | Sin contenido (eliminaciones y algunas actualizaciones) |
| 401    | No autenticado (token ausente, inválido o expirado) |
| 403    | Sin permisos (rol insuficiente) |
| 404    | Recurso no encontrado |
| 409    | Conflicto (optimistic lock o nombre de usuario duplicado) |
| 500    | Error interno del servidor |
