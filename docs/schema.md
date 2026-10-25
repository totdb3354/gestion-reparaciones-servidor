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
- `Bloque_compra` (0.9.9: cabecera de pedido a un proveedor; `Compra_componente.ID_BLOQUE` y, desde la 0.9.15, `Compra_otro.ID_BLOQUE` apuntan a ella)
- `Proveedor_tipo` (0.9.9: tipos de pieza que vende cada proveedor, por prefijo de la referencia; la usa la previsión por proveedor de «Nuevo pedido»)
- `Proveedor_tipo_excluido` (0.9.9: modelos de cada tipo que el proveedor no vende; sin filas = todos los modelos)
- `Compra_componente`
- `Compra_otro` (0.9.15: `ID_BLOQUE`, el bloque de su proveedor, como una pieza)
- `Reparacion_componente`
- `Solicitud_Stock`
- `Log_Actividad`
- `Movimiento_telefono`
- `Modelo_equivalencia`
- `Color_equivalencia`
- `Nsys_importacion` (0.9.17: un registro por fichero de NSYS procesado —Items, histórico o maestro—; alimenta el aviso de datos antiguos)
- `Nsys_movil` (0.9.17: la foto actual de NSYS, una fila por IMEI/SN y lote; desde la 0.9.22, con el coste de NSYS de cada lote —`COSTE`, solo para la plantilla de color—; sin precios de venta)
- `Nsys_evento` (0.9.17: historial de cambios de NSYS para el rastro del teléfono; solo se añade)
- `Nsys_producto` (0.9.17: el maestro de productos, «Goods + Grade» → modelo, color y SKU; desde la 0.9.22, con el «Product Name» tal cual —`NOMBRE`— para saber cómo se llama cada color en inglés)
- `Nsys_ubicacion` (0.9.17: traducción de cajas de NSYS a ubicaciones del ERP; datos, no código)
- `Taller_cambio` (0.9.21: lo del taller por encima de NSYS —color y grado— por IMEI, con su historia, quién y por qué, y su cierre automático cuando NSYS lo alcanza o lo cambia; ver `sql/migracion-sku-taller.sql`)
- `Taller_aviso` (0.9.21, pieza 2: lo que el taller avisa al guardar, editar o borrar una reparación sin cambiar el dato —color que no se deduce, grado que no se calcula, cambio deshecho o que ya no se puede deshacer—; solo se añade y lo lee el rastro; ver `sql/migracion-sku-taller.sql`)
