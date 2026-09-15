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
- Sin sesión: una petición sin cabecera `Authorization` recibe `403` (Spring Security sin entry point);
  con token inválido o caducado, `401` (filtro JWT). Los clientes tratan ambos como "sin sesión" cuando
  no hay token, y `401` como sesión caducada.
- Regenerar el snapshot: `mvn test -Dtest='OpenApiContractTest'` deja el contrato en
  `target/openapi.json`; la web lo copia a `api/openapi.json` y ejecuta `npm run api:types:offline`.
