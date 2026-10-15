Continuemos con El Jaibo POS. Usa tu revisión del repositorio como referencia del estado implementado y este mensaje como contexto de negocio.

Objetivo y forma de trabajo:

* Es un POS adaptable, cuyo primer uso será El Jaibo, un negocio ambulante de mariscos.
* MVP 100 % online, con una tablet y un celular que pueden vender simultáneamente.
* Conserva el stack, estructura y convenciones que encontraste. Los roles técnicos actuales son ADMIN y CASHIER.
* Trabaja directamente sobre el código, en incrementos pequeños y verificables. Explica qué cambiaste y cómo lo probaste.
* No reconstruyas módulos existentes ni cambies migraciones ya aplicadas: usa migraciones nuevas cuando corresponda.
* Conserva este contexto en un documento del repositorio y actualiza el avance para poder retomar en otros chats.

Reglas acordadas de ventas:

* Se pueden mantener varias cuentas abiertas simultáneamente, alternar entre ellas y agregar productos.
* Cada cuenta puede tener una referencia libre y un cliente opcional.
* Debemos permitir separar cuentas o mover partidas entre cuentas; puede implementarse después del flujo básico.
* Solo se permiten ventas con caja abierta.
* Debe quedar registrado quién realiza cada acción y desde qué terminal.
* Dos dispositivos pueden operar simultáneamente: hay que proteger los consecutivos, las partidas y los totales frente a concurrencia.
* Hay descuentos por partida y por venta, por importe o porcentaje. Cajero y administrador pueden aplicarlos.
* Las cortesías se reportan separadas de los descuentos comerciales.
* Las partidas conservan los datos comerciales de la venta aunque después cambie el catálogo.
* La eliminación de partidas es lógica y debe conservar su historial.

Productos y cantidades:

* Hay ventas por pieza, kilogramo, gramos, litro, bolsa y orden.
* Las presentaciones definen conversiones, cantidades variables y base del precio; por ejemplo, precio por kilogramo o por 100 gramos.
* Hay órdenes con precio fijo y botanas con productos y cantidades elegidos y precio final abierto.
* Los cocteles tienen tamaños chico, mediano y grande. Quitar un ingrediente no reduce automáticamente el precio.
* Se permitirán extras con cargo adicional.
* Respeta las reglas de precisión y las presentaciones ya implementadas.

Clientes, pagos y crédito — alcance posterior:

* Hay público general y clientes identificados, frecuentes o mayoristas.
* Los mayoristas pueden tener precios acordados individualmente.
* Se aceptan efectivo, transferencia y tarjeta; inicialmente solo necesitamos identificar el pago con tarjeta.
* Se permiten ventas a crédito y varios abonos.
* Los abonos reducen la deuda conservando el detalle de productos.
* Si existe saldo a favor, se pregunta cuánto aplicar a cada venta.
* Administrador y cajero pueden registrar abonos.
* Antes de implementar cobros, debemos precisar la transición entre cuenta abierta, venta pagada y venta a crédito: no deduzcas esa regla solo de los estados existentes.

Caja y periodos:

* El administrador realiza el cierre y puede reabrirlo. Conserva las reglas de caja que ya están implementadas.
* El periodo operativo principal es el fin de semana, normalmente viernes a domingo.
* Algunos movimientos de mercancía de lunes a jueves se atribuyen al ciclo anterior para iniciar el siguiente con existencias reales.
* Los gastos permanecen en el periodo en que se registran.
* Se requieren reportes diarios, por ciclo operativo y posteriormente mensuales si no complica el MVP.

Inventario, compras y traspasos — alcance posterior:

* Los productos simples pueden descontar inventario automáticamente.
* Los preparados no descontarán ingredientes por receta en el MVP; se registrará manualmente el consumo o ajuste al cierre.
* Se permite inventario negativo con control y merma manual por día.
* Las transformaciones complejas y rendimientos quedan fuera del primer alcance.
* Hay proveedores fijos y casuales, costos variables, compras a crédito y varios pagos por compra.
* Los costos solo son visibles para el administrador.
* El sobrante puede traspasarse a otro negocio a crédito, con precio por producto. Se reporta separado de ventas y pérdidas.
* Esa cuenta puede acumular nuevos traspasos y abonos; normalmente los abonos se aplican al saldo más antiguo.

Estado confirmado por tu revisión:

* Existen autenticación, terminales, caja, ciclos operativos, catálogo y clientes.
* Existen V1 a V10, pero falta verificar su aplicación en la base que se use.
* Existen Sale y SaleItem con lógica de dominio.
* Falta SaleEvent en Java, repositorios, servicios, DTO y endpoints de ventas.
* Faltan la generación segura del consecutivo y la coordinación de los totales.
* Todavía no se han ejecutado compilación ni pruebas durante esta revisión.

Siguiente incremento autorizado:

1. Ejecuta la verificación del proyecto y revisa Sale y SaleItem antes de construir encima. Distingue fallos de código de problemas del entorno.
2. Implementa el flujo mínimo para abrir una cuenta y consultar las cuentas abiertas, siguiendo las convenciones existentes.
3. Incluye los repositorios necesarios, consecutivo único por sesión de caja seguro ante concurrencia y registro del evento SALE_OPENED.
4. Valida que negocio, sucursal, usuario, terminal y caja correspondan entre sí; obtén la identidad del usuario autenticado.
5. Añade pruebas significativas del flujo, incluyendo caja cerrada, aislamiento entre negocios y generación concurrente del consecutivo.
6. Entrega los endpoints y ejemplos para probarlos en Postman.

Limita este incremento a apertura y consulta de cuentas. Después continuaremos con agregar partidas y recalcular totales; cobros, crédito e inventario se harán en incrementos posteriores.

Puedes comenzar con estas tareas.
