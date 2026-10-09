# Esquema de la base de datos

Fuente de verdad: `sql/crear_bd.sql` (y las migraciones de `sql/` para entornos existentes).
Tablas (generado con grep el 2026-09-14):

- `Componente`
- `Tecnico`
- `Cliente`
- `Telefono`
- `Usuario`
- `Revision`
- `Envio`
- `Envio_Telefono`
- `Reparacion`
- `Reparacion_borrador`
- `Proveedor`
- `Lote`
- `TipoCambio`
- `Dificultad_puntos`
- `Parametro`
- `Bloque_compra` (0.9.9: cabecera de pedido a un proveedor; `Compra_componente.ID_BLOQUE` apunta a ella)
- `Compra_componente`
- `Compra_otro`
- `Reparacion_componente`
- `Solicitud_Stock`
- `Log_Actividad`
- `Movimiento_telefono`
- `Modelo_equivalencia`
- `Color_equivalencia`
