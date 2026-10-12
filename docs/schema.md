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
- `Proveedor_tipo` (0.9.9: tipos de pieza que vende cada proveedor, por prefijo de la referencia; la usa la previsión por proveedor de «Nuevo pedido»)
- `Proveedor_tipo_excluido` (0.9.9: modelos de cada tipo que el proveedor no vende; sin filas = todos los modelos)
- `Compra_componente`
- `Compra_otro`
- `Reparacion_componente`
- `Solicitud_Stock`
- `Log_Actividad`
- `Movimiento_telefono`
- `Modelo_equivalencia`
- `Color_equivalencia`
