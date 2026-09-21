# Autorización de endpoints por rol

## Por qué existe este documento

La aplicación tiene tres roles: **TECNICO**, **SUPERTECNICO** y **ADMIN**. Quién puede hacer qué lo decide el servidor en cada petición; los clientes (JavaFX y web) se limitan a reflejarlo mostrando u ocultando acciones. Este documento explica el mecanismo y el criterio. El detalle de cada endpoint no se repite aquí: está en las anotaciones de los controllers y en los tests que se citan al final.

## Cómo funciona `@PreAuthorize`

`@PreAuthorize` es una anotación de Spring Security que se coloca sobre un método de un controller, o sobre la clase entera. Antes de ejecutar el método, Spring comprueba que el usuario de la petición cumple la condición; si no la cumple, responde `403 Forbidden` sin llegar al DAO ni a la base de datos.

```java
// Solo SUPERTECNICO
@PreAuthorize("hasRole('SUPERTECNICO')")
@PutMapping("/{idRep}")
public void editarReparacion(...) { ... }

// TECNICO o SUPERTECNICO
@PreAuthorize("hasAnyRole('TECNICO','SUPERTECNICO')")
@PostMapping
public void insertar(...) { ... }
```

Una anotación a nivel de clase vale para todos sus métodos. Para que `@PreAuthorize` funcione, la configuración lleva `@EnableMethodSecurity` (en `SecurityConfig`).

## El rol viaja en el JWT

Al iniciar sesión (`POST /api/auth/login`, la única ruta pública) el servidor firma un token que incluye el usuario, su rol y, si lo tiene, su técnico (`idTec`). En cada petición, `JwtAuthFilter` valida el token y construye con esos datos el `UsuarioPrincipal`: de él salen tanto el rol que evalúa `@PreAuthorize` como el técnico que usan las reglas de más abajo. Los controllers lo reciben con `@AuthenticationPrincipal`.

- Petición sin cabecera `Authorization`: `403`.
- Token inválido o caducado: `401`.
- Token válido sin el rol o la propiedad exigidos: `403`, con el motivo en `message` cuando lo decide una regla propia.

## Criterio general

- **Lecturas (GET):** exigen sesión y, donde su controller lo anota, también rol: las de administración (usuarios y logs: ADMIN), las solicitudes de pieza (SUPERTECNICO), las solicitudes de stock (SUPERTECNICO y ADMIN) y el detalle de edición de una reparación (SUPERTECNICO). Las listas del taller se acotan además por técnico con `FiltroTecnico`.
- **Escrituras (POST, PUT, PATCH, DELETE):** exigen el rol al que los clientes ofrecen esa acción.
  - **Gestión del taller** — asignar, reasignar, editar o eliminar trabajos, catálogo y stock de componentes, compras, gestión de solicitudes: **SUPERTECNICO**.
  - **Administración** — usuarios, logs, valores de dificultad: **ADMIN**.
  - **Trabajo propio del técnico** — completar, guardar una fila, registrar un componente agotado, borrador del formulario, "por cerrar", entrega y llegada de glass, crear una solicitud de stock: **TECNICO y SUPERTECNICO**, siempre sobre sus propias asignaciones.
- Un endpoint nuevo se anota en el mismo cambio que lo crea, con un test que compruebe al menos un rol admitido y uno no admitido.

## Regla del `?tecnico=` (`FiltroTecnico`)

`FiltroTecnico.efectivo(principal, tecnico)` decide qué técnico filtra el DAO en las seis listas del taller (`GET /api/reparaciones/historial`, `/api/reparaciones/asignaciones`, `/api/glass/historial`, `/api/glass/asignaciones`, `/api/pulidos/historial`, `/api/pulidos/asignaciones`) y en `GET /api/reparaciones/pendientes/contadores`:

- **TECNICO**: el filtro es siempre su `idTec`. Sin parámetro o con su propio id recibe lo suyo; si pide otro técnico, `403` con el motivo "Solo puedes consultar tus propios trabajos" (sin llegar al DAO). Un TECNICO sin `idTec` también recibe `403`. Cualquier rol que no sea SUPERTECNICO ni ADMIN se trata como TECNICO.
- **SUPERTECNICO y ADMIN**: filtro libre (sin parámetro, todos; con parámetro, ese técnico).
- **Contadores**: sin `?tecnico=` se cuentan los del técnico del token, también para el SUPERTECNICO; un ADMIN sin técnico recibe `{0, 0, 0}` sin consultar.

## Regla de las escrituras del formulario (`PropiedadAsignacion`)

`PropiedadAsignacion` vive junto a `FiltroTecnico` y se aplica en `POST /api/reparaciones/completa`, `POST /api/reparaciones/{idAsignacion}/filas`, `POST /api/reparaciones/{idAsignacion}/agotar-componente`, `PATCH /api/reparaciones/{idRep}/completar` y `GET|PUT|DELETE /api/reparaciones/{idRep}/borrador` (la clave del borrador es el id de la asignación):

- **El técnico de cada trabajo es el del token.** `tecnicoEfectivo(principal, idTecDueno)` devuelve siempre el `idTec` del token; el `idTec` que envíe el cliente en el cuerpo no interviene (se acepta, para que los clientes anteriores sigan funcionando, y se ignora).
- **Solo se trabaja sobre asignaciones propias**, igual para TECNICO y SUPERTECNICO: si la asignación es de otro técnico, o el token no tiene técnico, `403` con el motivo "Solo puedes trabajar sobre tus propias asignaciones". Si la asignación no existe, la regla deja pasar y el DAO responde su `409` de asignación ya eliminada o completada. El borrador, además, solo admite los roles TECNICO y SUPERTECNICO.
- **La edición es del supertécnico.** `PUT /api/reparaciones/{idRep}` y `GET /api/reparaciones/{idRep}/detalle-edicion` exigen SUPERTECNICO. Al añadir filas o acciones a una reparación ya hecha, los clientes llaman a `completa` sin `idAsignacion` y con el `idTec` del técnico **original**: ese caso pasa por `exigirSupertecnico` (`403` con el motivo "Solo el supertécnico puede corregir una reparación ya hecha" para los demás roles) y conserva el `idTec` del cuerpo, para que el trabajo siga a nombre de quien lo hizo.

## Dónde están los tests

| Qué | Test |
|---|---|
| Regla del `?tecnico=` | `security/FiltroTecnicoTest`, `controller/FiltroTecnicoControllersTest`, `controller/ReparacionControllerContadoresTest` |
| Regla de propiedad de la asignación | `security/PropiedadAsignacionTest`, `controller/PropiedadAsignacionControllersTest` |
| Roles de la edición y del borrador (cadena de seguridad real, MockMvc) | `controller/RolesReparacionFormularioTest` |
| Roles de solicitudes de stock y del ajuste de stock (MockMvc) | `controller/RolesSolicitudesStockTest` |
| Dueño de la asignación en la entrega y la llegada de glass | `controller/ReparacionControllerEntregaGlassTest` |
| Sin sesión (`403`) y token inválido (`401`) | `OpenApiContractTest.elContextoArrancaYElContratoExigeSesion` |

Los tests de controller que llaman al método Java directamente (con DAOs de Mockito) comprueban las reglas propias, pero no ejercitan `@PreAuthorize`; los roles se comprueban con MockMvc y un token generado con `JwtUtil`, sin base de datos.
