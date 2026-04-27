# Esquema de base de datos — Gestión Reparaciones

El script fuente está en `gestion-reparaciones-cliente/src/main/resources/sql/crear_bd.sql`.  
Es destructivo y regenerable: borra y recrea todas las tablas.

**Charset:** `utf8mb4` / `utf8mb4_unicode_ci`

---

## Diagrama de relaciones

```
Tecnico ──────────────── Usuario
   │                      (rol: ADMIN | TECNICO)
   │
   ├──── Reparacion ──── Telefono (IMEI)
   │          │
   │          └──── Reparacion_componente ──── Componente
   │                     (piezas, incidencias,
   │                      solicitudes de piezas)
   │
   └──── (vía Reparacion)

Componente ──── Compra_componente ──── Proveedor
                                            │
                                       TipoCambio (DIVISA, FECHA)
```

---

## Tablas

### `Tecnico`
Persona física que trabaja en el taller.

| Columna | Tipo | Descripción |
|---------|------|-------------|
| ID_TEC | INT AUTO_INCREMENT | PK |
| NOMBRE | VARCHAR(100) | Nombre completo |
| ACTIVO | BOOLEAN | Desactivar en lugar de borrar si tiene historial |

---

### `Usuario`
Credenciales de acceso al sistema.

| Columna | Tipo | Descripción |
|---------|------|-------------|
| ID_USU | INT AUTO_INCREMENT | PK |
| NOMBRE_USUARIO | VARCHAR(50) UNIQUE | Login |
| PASSWORD | VARCHAR(255) | Hash BCrypt |
| ROL | ENUM | `ADMIN` o `TECNICO` |
| ID_TEC | INT NULL | FK → Tecnico. NULL si el usuario es ADMIN puro |

**Nota:** ADMIN puede no tener técnico asociado. Un TECNICO siempre tiene ID_TEC.

---

### `Telefono`
Registro de IMEIs que han entrado al taller.

| Columna | Tipo | Descripción |
|---------|------|-------------|
| IMEI | VARCHAR(15) | PK |

Solo existe para cumplir la FK de Reparacion. Se inserta automáticamente si no existe al crear una reparación.

---

### `Reparacion`
Una reparación o asignación (abierta o completada).

| Columna | Tipo | Descripción |
|---------|------|-------------|
| ID_REP | VARCHAR(30) | PK. Formato: `R{yyyyMMdd}_{n}` (reparación) o `A{yyyyMMdd}_{n}` (asignación) |
| FECHA_ASIG | DATETIME | Fecha de inicio/asignación |
| FECHA_FIN | DATETIME NULL | NULL = abierta. Rellena al completar |
| IMEI | VARCHAR(15) | FK → Telefono |
| ID_TEC | INT | FK → Tecnico |
| ID_REP_ANTERIOR | VARCHAR(30) NULL | FK → Reparacion. Enlaza con reparación previa del mismo IMEI (incidencias) |
| UPDATED_AT | TIMESTAMP | Gestión de concurrencia (optimistic lock) |

**Prefijos de ID:**
- `R` = reparación nueva (viene del formulario, directamente reparada)
- `A` = asignación (el técnico la recibe, trabaja, y luego crea la reparación real)

---

### `Reparacion_componente`
Piezas usadas en una reparación. También modela incidencias y solicitudes de piezas.

| Columna | Tipo | Descripción |
|---------|------|-------------|
| ID_RC | INT AUTO_INCREMENT | PK |
| ID_REP | VARCHAR(30) | FK → Reparacion |
| ID_COM | INT NULL | FK → Componente. NULL si es solicitud sin componente asignado |
| ES_REUTILIZADO | BOOLEAN | La pieza se reutilizó (no consume stock) |
| ES_INCIDENCIA | BOOLEAN | Esta fila representa una incidencia |
| ES_RESUELTO | BOOLEAN | La incidencia fue resuelta |
| INCIDENCIA | TEXT NULL | Texto de la incidencia |
| OBSERVACIONES | TEXT NULL | Observaciones del técnico |
| ES_SOLICITUD | BOOLEAN | El técnico está solicitando esta pieza al admin |
| DESCRIPCION_SOLICITUD | TEXT NULL | Qué pieza necesita el técnico |
| ESTADO_SOLICITUD | ENUM | `PENDIENTE` / `GESTIONADA` / `RECHAZADA` |
| CANTIDAD | INT | Unidades usadas (default 1) |
| UPDATED_AT | TIMESTAMP | Timestamp de última modificación |

---

### `Componente`
Piezas de stock del taller.

| Columna | Tipo | Descripción |
|---------|------|-------------|
| ID_COM | INT AUTO_INCREMENT | PK |
| TIPO | VARCHAR(100) UNIQUE | SKU/nombre. Formato: `[prefijo][modelo][color?]` |
| STOCK | INT | Unidades disponibles actuales |
| STOCK_MINIMO | INT | Umbral de alerta de stock bajo |
| ACTIVO | BOOLEAN | Desactivar en lugar de borrar si tiene historial |
| FECHA_REGISTRO | TIMESTAMP | Fecha de alta en el sistema |
| UPDATED_AT | TIMESTAMP | Gestión de concurrencia (optimistic lock) |

**Prefijos SKU:** `bat`=Batería, `cha`=Carcasa, `g`=Cristal, `cam`=Cámara, `lcd`=Pantalla, `mc`=Micrófono, `otro`=Otros.

---

### `Proveedor`
Empresas a las que se compran los componentes.

| Columna | Tipo | Descripción |
|---------|------|-------------|
| ID_PROV | INT AUTO_INCREMENT | PK |
| NOMBRE | VARCHAR(100) | Nombre del proveedor |
| ACTIVO | BOOLEAN | Desactivar en lugar de borrar |
| DIVISA | VARCHAR(3) | Divisa por defecto del proveedor (ej. `EUR`, `USD`, `CNY`) |

---

### `TipoCambio`
Tasas de cambio históricas para convertir precios de compra a EUR.

| Columna | Tipo | Descripción |
|---------|------|-------------|
| DIVISA | VARCHAR(3) | PK compuesta |
| FECHA | DATE | PK compuesta |
| TASA | DECIMAL(10,6) | 1 EUR = TASA unidades de DIVISA |

Se inserta manualmente o vía endpoint cuando cambia la tasa.

---

### `Compra_componente`
Pedidos de reposición de stock.

| Columna | Tipo | Descripción |
|---------|------|-------------|
| ID_COMPRA | INT AUTO_INCREMENT | PK |
| ID_COM | INT | FK → Componente |
| ID_PROV | INT | FK → Proveedor |
| CANTIDAD | INT | Unidades pedidas |
| CANTIDAD_RECIBIDA | INT NULL | Unidades realmente recibidas (puede ser parcial) |
| ES_URGENTE | BOOLEAN | Marcado como urgente |
| FECHA_PEDIDO | DATETIME | Cuándo se hizo el pedido |
| FECHA_LLEGADA | DATETIME NULL | Cuándo llegó (NULL = pendiente) |
| PRECIO_UNIDAD_PEDIDO | DECIMAL(10,2) | Precio en la divisa del pedido |
| DIVISA | VARCHAR(3) | Divisa del precio (`EUR`, `USD`, etc.) |
| PRECIO_EUR | DECIMAL(10,2) | Precio convertido a EUR en el momento del pedido |
| ESTADO | ENUM | `pendiente` / `recibido` / `parcial` / `cancelado` |
| UPDATED_AT | TIMESTAMP | Gestión de concurrencia (optimistic lock) |

---

## Convenciones del esquema

**Soft delete:** `Tecnico`, `Componente` y `Proveedor` tienen columna `ACTIVO`. No se borran si tienen historial — se desactivan.

**Optimistic lock:** `Componente`, `Compra_componente` y `Reparacion` tienen `UPDATED_AT`. El cliente envía el timestamp que leyó; el servidor rechaza con 409 si difiere (otro cliente modificó el registro).

**ENUMs:**
- `Usuario.ROL`: `ADMIN`, `TECNICO`
- `Compra_componente.ESTADO`: `pendiente`, `recibido`, `parcial`, `cancelado`
- `Reparacion_componente.ESTADO_SOLICITUD`: `PENDIENTE`, `GESTIONADA`, `RECHAZADA`

**Formato IDs de reparación:** `R20250427_1` — prefijo + fecha yyyyMMdd + contador diario. Generado en Java (servidor), no en BD.
