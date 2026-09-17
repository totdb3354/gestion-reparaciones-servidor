# Autorización de endpoints por rol

---

## Por qué existe este documento

La aplicación tiene tres roles: **TECNICO**, **SUPERTECNICO** y **ADMIN**. La interfaz gráfica (cliente JavaFX) ya oculta o deshabilita las acciones que cada rol no puede realizar. Sin embargo, eso no es suficiente: cualquier usuario con un token JWT válido podría saltarse la UI y llamar directamente a la API con herramientas como Postman o curl.

Este tipo de ataque se llama **privilege escalation** (escalada de privilegios): un TECNICO autenticado llama a un endpoint de SUPERTECNICO directamente, sin pasar por la UI que lo bloquearía.

Para evitarlo, el servidor aplica una segunda capa de control usando `@PreAuthorize` de Spring Security — la autorización no solo vive en el cliente, sino también en el servidor.

---

## Cómo funciona `@PreAuthorize`

`@PreAuthorize` es una anotación de Spring Security que se coloca encima de un método (o de una clase entera) en un controller. Antes de ejecutar el método, Spring comprueba si el usuario autenticado cumple la condición indicada. Si no la cumple, devuelve automáticamente un `403 Forbidden` sin llegar al DAO ni a la base de datos.

```java
// Solo SUPERTECNICO puede llamar a este endpoint
@PreAuthorize("hasRole('SUPERTECNICO')")
@PostMapping("/asignaciones")
public Map<String, Object> insertarAsignacion(...) { ... }

// SUPERTECNICO o ADMIN pueden llamar a este endpoint
@PreAuthorize("hasAnyRole('SUPERTECNICO', 'ADMIN')")
@GetMapping("/cantidad-pendiente/{idCom}")
public Map<String, Object> getCantidadPendiente(...) { ... }
```

El rol del usuario viene del token JWT — se incluye en el token al hacer login y Spring Security lo lee automáticamente en cada petición a través de `JwtAuthFilter`.

Para que `@PreAuthorize` funcione, la clase de configuración debe tener `@EnableMethodSecurity` (ya presente en `SecurityConfig.java`).

---

## Criterio general

- **GET (lectura)**: accesibles a cualquier rol autenticado. No se restringe por rol porque los datos no son sensibles externamente y el atacante potencial ya es un empleado de confianza con credenciales válidas.
- **Mutaciones (POST, PUT, PATCH, DELETE)**: restringidas por rol según la tabla de permisos. Aquí está el daño potencial real — crear, modificar o borrar datos sin autorización.

---

## AuthController — `/api/auth`

| Endpoint | Método | Rol requerido |
|---|---|---|
| `/login` | POST | público (sin token) |

---

## UsuarioController — `/api/usuarios`

> `@PreAuthorize("hasRole('ADMIN')")` en todos los métodos.

| Endpoint | Método | Rol requerido |
|---|---|---|
| `/` | GET | ADMIN |
| `/registrar` | POST | ADMIN |
| `/activar/{id}` | PATCH | ADMIN |
| `/desactivar/{id}` | PATCH | ADMIN |
| `/excluir-estadisticas/{id}` | PATCH | ADMIN |
| `/incluir-estadisticas/{id}` | PATCH | ADMIN |
| `/rol/{id}` | PATCH | ADMIN |
| `/{id}` | DELETE | ADMIN |

---

## LogController — `/api/logs`

> `@PreAuthorize("hasRole('ADMIN')")` a nivel de clase.

| Endpoint | Método | Rol requerido |
|---|---|---|
| `/` | GET | ADMIN |

---

## CompraController — `/api/compras`

| Endpoint | Método | Rol requerido |
|---|---|---|
| `/` | GET | SUPERTECNICO |
| `/pendientes` | GET | SUPERTECNICO |
| `/cantidad-pendiente/{idCom}` | GET | SUPERTECNICO, ADMIN |
| `/` | POST | SUPERTECNICO |
| `/{idCompra}` | PUT | SUPERTECNICO |
| `/{idCompra}/confirmar-recibido` | PATCH | SUPERTECNICO |
| `/{idCompra}/confirmar-parcial` | PATCH | SUPERTECNICO |
| `/{idCompra}/recibir-resto` | PATCH | SUPERTECNICO |
| `/{idCompra}/confirmar-alterado` | PATCH | SUPERTECNICO |
| `/{idCompra}/cancelar` | PATCH | SUPERTECNICO |
| `/{idCompra}/desrecibir` | PATCH | SUPERTECNICO |

> `GET /cantidad-pendiente/{idCom}` abierto a ADMIN porque `StockController` lo llama al hacer clic en una fila de stock (vista de solo lectura).

---

## ProveedorController — `/api/proveedores`

> `@PreAuthorize("hasRole('SUPERTECNICO')")` a nivel de clase.

| Endpoint | Método | Rol requerido |
|---|---|---|
| `/` | GET | SUPERTECNICO |
| `/activos` | GET | SUPERTECNICO |
| `/{idProv}/tiene-pedidos` | GET | SUPERTECNICO |
| `/` | POST | SUPERTECNICO |
| `/{idProv}/activo` | PATCH | SUPERTECNICO |
| `/{idProv}/divisa` | PATCH | SUPERTECNICO |
| `/{idProv}` | DELETE | SUPERTECNICO |

---

## SolicitudController — `/api/solicitudes`

> `@PreAuthorize("hasRole('SUPERTECNICO')")` a nivel de clase.

| Endpoint | Método | Rol requerido |
|---|---|---|
| `/count` | GET | SUPERTECNICO |
| `/` | GET | SUPERTECNICO |
| `/{idRc}/estado` | PATCH | SUPERTECNICO |
| `/{idRc}/limpiar` | PATCH | SUPERTECNICO |

---

## ComponenteController — `/api/componentes`

| Endpoint | Método | Rol requerido | Nota |
|---|---|---|---|
| `/` | GET | cualquiera | — |
| `/gestionados` | GET | cualquiera | — |
| `/stock-bajo` | GET | cualquiera | — |
| `/agrupados` | GET | cualquiera | TECNICO lo usa en formulario de reparación |
| `/chasis` | GET | cualquiera | TECNICO lo usa en formulario de reparación |
| `/evolucion-stock` | GET | cualquiera | — |
| `/` | POST | SUPERTECNICO | — |
| `/{idCom}` | PUT | SUPERTECNICO | — |
| `/{idCom}/stock-minimo` | PATCH | SUPERTECNICO | — |
| `/{idCom}/stock` | PATCH | cualquiera | TECNICO lo llama al guardar una reparación |
| `/{idCom}/activo` | PATCH | SUPERTECNICO | — |
| `/{idCom}` | DELETE | SUPERTECNICO | — |

---

## ReparacionController — `/api/reparaciones`

| Endpoint | Método | Rol requerido | Nota |
|---|---|---|---|
| `/` | GET | cualquiera | — |
| `/imei/{imei}` | GET | cualquiera | — |
| `/imei/{imei}/count` | GET | cualquiera | — |
| `/historial` | GET | cualquiera | `?tecnico=` con la regla de `FiltroTecnico` (ver abajo) |
| `/historial/imei/{imei}` | GET | cualquiera | — |
| `/asignaciones` | GET | cualquiera | `?tecnico=` con la regla de `FiltroTecnico` (ver abajo) |
| `/pendientes/contadores` | GET | cualquiera | `{reparaciones, glass, pulidos}` abiertas del técnico efectivo: sin `?tecnico=`, el del token (también el SUPERTECNICO); ADMIN sin `?tecnico=` recibe ceros sin consultar; con `?tecnico=`, regla de `FiltroTecnico` |
| `/asignaciones/{idRep}` | GET | cualquiera | — |
| `/asignaciones/{idAsig}/solicitudes` | GET | cualquiera | — |
| `/{idRep}/detalle-edicion` | GET | cualquiera | — |
| `/{idRep}/referenciadora` | GET | cualquiera | — |
| `/imei/{imei}/ya-reparados` | GET | cualquiera | — |
| `/imei/{imei}/incidencia-activa` | GET | cualquiera | — |
| `/imei/{imei}/tiene-asignacion` | GET | cualquiera | — |
| `/estadisticas` | GET | cualquiera | — |
| `/estadisticas/puntos` | GET | cualquiera | — |
| `/` | POST | cualquiera | TECNICO crea sus propias reparaciones |
| `/asignaciones` | POST | SUPERTECNICO | — |
| `/completa` | POST | cualquiera | TECNICO completa su reparación |
| `/{idRep}/completar` | PATCH | cualquiera | — |
| `/asignaciones/{idRep}/tecnico` | PATCH | SUPERTECNICO | — |
| `/{idRep}` | PUT | cualquiera | TECNICO edita su propia reparación |
| `/{idRep}/incidencia` | POST | SUPERTECNICO | — |
| `/imei/{imei}/incidencia-activa` | DELETE | SUPERTECNICO | — |
| `/{idAsignacion}/agotar-componente` | POST | cualquiera | TECNICO solicita pieza agotada |
| `/asignaciones/{idAsig}` | DELETE | SUPERTECNICO | — |
| `/{idRep}` | DELETE | SUPERTECNICO | — |

---

## GlassController — `/api/glass`

| Endpoint | Método | Rol requerido | Nota |
|---|---|---|---|
| `/asignaciones` | GET | cualquiera | `?tecnico=` con la regla de `FiltroTecnico` |
| `/historial` | GET | cualquiera | `?tecnico=` con la regla de `FiltroTecnico` |
| `/asignaciones` | POST | SUPERTECNICO | — |

> Completar, editar y borrar glass van por `ReparacionController` (operan por ID).

---

## PulidoController — `/api/pulidos`

| Endpoint | Método | Rol requerido | Nota |
|---|---|---|---|
| `/asignaciones` | GET | cualquiera | `?tecnico=` con la regla de `FiltroTecnico` |
| `/historial` | GET | cualquiera | `?tecnico=` con la regla de `FiltroTecnico` |
| `/asignaciones` | POST | SUPERTECNICO | — |
| `/asignaciones/completar-lote` | POST | cualquiera | — |
| `/asignaciones/{idAP}` | PATCH | SUPERTECNICO | — |
| `/asignaciones/{idAP}` | DELETE | SUPERTECNICO | — |
| `/historial/{idP}` | DELETE | SUPERTECNICO | Motivo opcional en el cuerpo |

---

## Regla del `?tecnico=` (`FiltroTecnico`)

`FiltroTecnico.efectivo(principal, tecnico)` decide qué técnico filtra el DAO en las seis listas del taller (`GET /api/reparaciones/historial`, `/api/reparaciones/asignaciones`, `/api/glass/historial`, `/api/glass/asignaciones`, `/api/pulidos/historial`, `/api/pulidos/asignaciones`) y en `GET /api/reparaciones/pendientes/contadores`:

- **TECNICO**: el filtro es siempre su `idTec`. Sin parámetro o con su propio id recibe lo suyo; si pide otro técnico, `403` con el motivo "Solo puedes consultar tus propios trabajos" (sin llegar al DAO). Un TECNICO sin `idTec` también recibe `403`. Cualquier rol que no sea SUPERTECNICO ni ADMIN se trata como TECNICO.
- **SUPERTECNICO y ADMIN**: filtro libre (sin parámetro, todos; con parámetro, ese técnico).
- **Contadores**: sin `?tecnico=` se cuentan los del técnico del token, también para el SUPERTECNICO; un ADMIN sin técnico recibe `{0, 0, 0}` sin consultar.

---

## ReparacionComponenteController — `/api/reparacion-componentes`

| Endpoint | Método | Rol requerido | Nota |
|---|---|---|---|
| `/{idRep}` | GET | cualquiera | — |
| `/` | POST | cualquiera | TECNICO añade componentes a su reparación |
| `/{idRep}/{idCom}` | DELETE | cualquiera | TECNICO elimina componente de su reparación |
| `/{idRep}/incidencia` | PATCH | SUPERTECNICO | — |
| `/{idRep}/incidencia` | DELETE | SUPERTECNICO | — |

---

## DificultadController — `/api/valores-dificultad`

| Endpoint | Método | Rol requerido |
|---|---|---|
| `/` | GET | cualquiera |
| `/` | PUT | ADMIN |

---

## TecnicoController, TelefonoController, TipoCambioController

Sin restricción de rol a nivel de clase — solo autenticación JWT. Algunos métodos sí llevan `@PreAuthorize` (p. ej. `PATCH /api/tecnicos/{idTec}/glass` y las ediciones de teléfono como `/observacion`, `/cliente` o `/atributos`, SUPERTECNICO).
