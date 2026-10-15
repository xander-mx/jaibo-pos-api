# El Jaibo POS — avance

## Contexto y alcance

Las reglas recibidas se conservan completas en [CONTEXTO_NEGOCIO.md](CONTEXTO_NEGOCIO.md).
Ese documento es el contexto original; este archivo registra el estado posterior de implementación.

Incremento del 8 de septiembre de 2026: apertura y listado de cuentas abiertas.
Se conserva Java 21, Spring Boot 4.1.1, Maven, PostgreSQL, JPA, Flyway y la estructura por módulo/capa.

## Implementado

- `POST /api/v1/sales/open`: abre una cuenta con caja explícita, referencia libre y cliente opcional.
- `GET /api/v1/sales/open`: lista las cuentas `OPEN` de la sucursal de la terminal autenticada.
  Es compartido entre terminales de la sucursal; incluye cuentas abiertas de cualquier sesión,
  incluso si posteriormente se cerró su caja. Consultar no exige caja abierta.
  No se decidió trasladar, finalizar ni cancelar cuentas al cerrar caja.
- Usuario y negocio proceden del JWT. Se comprueba usuario activo, pertenencia al negocio,
  negocio activo, terminal activa, sucursal activa, caja de la misma sucursal y cliente activo del negocio.
- `SaleRepository`, `SaleEventRepository`, `SaleEvent` y `SaleEventType.SALE_OPENED`.
  Los repositorios nuevos no se exportan automáticamente por Spring Data REST.
- Creación de cuenta y evento en una sola transacción. El evento conserva usuario, terminal,
  fecha y número de cuenta. Los importes iniciales son cero.
- Consecutivo por sesión: bloqueo pesimista de la fila de caja y `MAX(sale_number) + 1`
  dentro de la misma transacción. La restricción única de V9 queda como protección adicional.
  Se consideran todas las ventas de la sesión, independientemente del estado.
- Cierre y reapertura existentes usan la misma consulta bloqueante del repositorio de caja.
  Esto serializa una apertura con un cierre concurrente sin cambiar sus reglas de autorización.

## Revisión de Sale y SaleItem

Se conservó su código. Ambas entidades compilan y sus mappings se validan contra V1–V10.
`Sale` comienza en OPEN y ya calcula descuentos globales y cortesías separadas.
`SaleItem` conserva datos comerciales, calcula importes y permite eliminación lógica.

Antes del siguiente incremento, resolver y probar:

- Coordinación transaccional de partidas y totales de cabecera, con control de concurrencia.
- `Sale.refreshTotals` recibe importes externos: no agrega automáticamente las partidas.
- La validación de cambios de cantidad en `SaleItem` consulta la presentación y unidad actuales;
  evaluar cómo conservar también las reglas históricas si se modifica el catálogo.
- Las validaciones de usuario/terminal de algunas operaciones de dominio son parciales;
  los servicios de partidas deberán validar identidad y pertenencia igual que apertura.
- El enum de eventos Java incluye solo el evento implementado en este incremento;
  ampliar junto con los próximos flujos previstos en V9.

No se implementaron partidas, traslado/separación, cobros, crédito ni inventario.
Antes de cobros se debe acordar explícitamente la transición entre abierta, pagada y crédito.
Los ciclos actuales siguen siendo de viernes a jueves; se conserva esa implementación aunque
la actividad comercial principal sea de viernes a domingo.

## Verificación

- Línea base: 7 pruebas aprobadas, sin fallos, errores ni omisiones.
- El wrapper de Windows falló antes de compilar con `Cannot index into a null array`
  al acceder a `.Target[0]`. Se usó la instalación local de Maven 3.9.16 sin modificar el wrapper.
- Docker requiere ejecución fuera del sandbox; la verificación se autorizó con Maven.
- Flyway aplicó V1–V10 y Hibernate validó el esquema en PostgreSQL 18 de Testcontainers.
  No se verificó ni modificó la base local operativa. No se agregaron ni editaron migraciones.
- Resultado del incremento: `mvn test` terminó con BUILD SUCCESS: 14 pruebas,
  cero fallos, errores u omisiones (7 existentes y 7 nuevas).
  Las nuevas cubren apertura y auditoría, consulta compartida entre dispositivos,
  caja cerrada, aislamiento entre negocios y sucursales, cliente/terminal/caja ajenos,
  identidad inconsistente, usuario inactivo, autenticación, validación del request y
  ocho aperturas concurrentes desde dos terminales, con consecutivos 1–8 y ocho eventos.

Pruebas reproducibles con Java 21, Maven 3.9.16 y Docker disponibles: `mvn test`.
En un entorno donde el wrapper funcione también puede usarse `./mvnw test`.

Ejemplos de uso: [POSTMAN_CUENTAS.md](POSTMAN_CUENTAS.md).

## Pruebas de pagos de compras — 6 de octubre de 2026

Se revisaron PurchasePaymentService, las entidades de compras/pagos/caja, sus repositorios,
el cierre de caja y las pruebas/configuración existentes. Se agregó únicamente código de
pruebas en `PurchasePaymentServiceIntegrationTest`; no se modificaron producción ni migraciones.

- Dos pagos de 80 sobre una compra RECEIVED de 100: uno persiste, el segundo recibe 409 por
  saldo insuficiente. Se usan fondos EXTERNAL para aislar la protección del bloqueo de compra;
  el total final es 80 y no hay movimientos de caja.
- Pago desde caja contra cierre: se comprueban ambos órdenes. Pago primero deja un pago y
  una salida PURCHASE_PAYMENT por 80, con cierre esperado de 420 sobre apertura de 500.
  Cierre primero rechaza el pago con 409 y deja esperado de 500, sin pagos ni movimientos.
- Compra liquidada con pagos de 40 y 60: un abono adicional de 0.01 recibe 409; permanecen
  exactamente dos pagos y dos salidas de caja por un total de 100.

ExecutorService y CountDownLatch coordinan transacciones REQUIRES_NEW con READ_COMMITTED.
Las pruebas mantienen la transacción que obtiene primero el bloqueo hasta comprobar
`pg_blocking_pids` en PostgreSQL; así verifican espera real sin depender del orden del planificador.
Se consultan directamente purchase_payment, cash_movement y cash_session tras terminar las transacciones.
Se reutiliza PostgreSQL 18 de Testcontainers, sin H2 ni repositorios simulados.

Validación: suite existente de 90 pruebas aprobada; luego 4 ejecuciones nuevas aprobadas,
sin fallos, errores u omisiones. Ambas ejecuciones terminaron con BUILD SUCCESS usando Maven
3.9.16 y Java 21. Flyway validó/aplicó V1–V31 en la base de pruebas aislada.
Comando del nuevo grupo: `mvn -Dtest=PurchasePaymentServiceIntegrationTest test`.

## Administración de usuarios — 6 de octubre de 2026

Se agregaron `GET /api/v1/users` y `POST /api/v1/users`, exclusivos de ADMIN.
Se conserva AppUser y sus roles ADMIN/CASHIER. El negocio viene del JWT y se comprueba
la identidad, actividad y rol actual del administrador en la base de datos.
Los nuevos usuarios nacen activos y las respuestas contienen únicamente DTOs sin credenciales.
El listado incluye activos e inactivos del negocio autenticado, ordenados por username.

La unicidad de username continúa siendo global e insensible a mayúsculas, conforme a
V1 y al login existente. Se reutiliza PasswordEncoder BCrypt, con validación Jakarta
del límite de 72 bytes UTF-8, y se conserva exactamente la contraseña recibida.
Se maneja también la colisión de registros concurrentes con 409.

Se deshabilitó la exposición automática de AppUserRepository por Spring Data REST.
Las pruebas revelaron que el manejador genérico convertía acceso denegado, JSON inválido
y rutas inexistentes en 500; se agregaron manejadores específicos 403/400/404 sin
exponer valores sensibles. No se cambiaron login, entidades de usuario ni migraciones.

Resultado: suite completa `mvn test`, BUILD SUCCESS, 117 pruebas sin fallos, errores
u omisiones; 23 ejecuciones nuevas en UserControllerIntegrationTest. PostgreSQL 18
mediante Testcontainers, repositorios reales, incluido login real de usuarios creados.
Quedan fuera edición, desactivación y cambio de contraseñas.
Contrato y ejemplo: [USUARIOS.md](USUARIOS.md).

## Activación de usuarios y estado de JWT — 6 de octubre de 2026

Se agregaron PATCH `/api/v1/users/{userId}/activate` y `/deactivate`.
Son exclusivos de ADMIN activo, limitados al negocio del JWT y devuelven UserResponse.
Se reutilizan activate/deactivate de AppUser, con operaciones idempotentes y logs de
administrador, usuario afectado, negocio, estado y cambio realizado. No se borran usuarios
ni referencias históricas. No se implementaron edición ni cambio de contraseñas.

Las modificaciones bloquean pesimistamente el negocio dentro de @Transactional antes
de consultar al administrador y al objetivo. Se rechaza auto-desactivación, se comprueba
el conteo de administradores activos y se validan permisos nuevamente después del bloqueo.
Dos administradores que intentan desactivarse mutuamente dejan uno activo; quien esperó
el bloqueo y ya está inactivo recibe 403 en llamadas directas al servicio.
El último administrador está protegido tanto por auto-desactivación como por el conteo.

Se confirmó que los JWT previos antes podían acceder a rutas que solo validaban el token.
ActiveUserFilter ahora comprueba usuario activo, negocio activo y pertenencia en una única
consulta de proyección por solicitud HTTP protegida, tras la validación criptográfica.
Usa el rol vigente de la base de datos, mantiene Jwt como principal y rechaza identidades
inactivas/inconsistentes con 401. Se actualizaron las expectativas antiguas de pruebas
de usuarios y ventas que esperaban 403 para esos casos.

ValidatedUserIdentity dura solo la solicitud; listado/registro reutilizan esa comprobación.
Las operaciones que esperan el bloqueo hacen una validación fresca dentro de la transacción.
No se cambió el login ni JwtDecoder. Login público, health, info y handshake /ws conservan
su acceso; WebSockets conserva la validación de identidad/terminal en CONNECT. El filtro
HTTP no desconecta conexiones WebSocket ya abiertas. Al reactivar, los tokens previos
todavía vigentes vuelven a ser admisibles; no se implementó revocación permanente de tokens.

Validación final: `mvn test`, BUILD SUCCESS, 126 pruebas sin fallos, errores u omisiones.
UserActivationIntegrationTest aporta 9 pruebas de integración con PostgreSQL 18 de
Testcontainers, JWT reales y concurrencia con transacciones independientes. Cubre
desactivación, login rechazado, rechazo de JWT previo en catálogo/me/clientes, reactivación,
historial, permisos, último administrador, aislamiento, idempotencia y rutas públicas.
La medición SQL comprueba una consulta en /auth/me y dos en /users (identidad y listado),
sin consulta redundante del administrador. Las pruebas existentes de WebSockets siguen pasando.
No se modificaron migraciones ni la base operativa.

## Revocación de sesiones WebSocket — 7 de octubre de 2026

Se inspeccionaron el endpoint /ws, la autenticación STOMP, el broker simple y la
publicación AFTER_COMMIT de cambios de cuentas. CONNECT ya validaba la actividad;
las conexiones existentes no volvían a comprobarla al recibir publicaciones.

UserService ahora emite UserDeactivated dentro de su transacción. Un listener
AFTER_COMMIT invalida y cierra todas las conexiones locales del usuario y negocio.
Se agregó un registro de transportes, validación fresca en SUBSCRIBE y comprobación
en memoria del canal de salida, también al despachar eventos que estaban en cola.
El cierre usa 1008. Un rollback no revoca conexiones y reactivar requiere reconectar.
Se rechaza SEND de clientes, pues no existen handlers para publicaciones de cliente.
Se conservan terminal, aislamiento por sucursal, formato y destino de eventos de cuentas.

Para cambios confirmados desde otra instancia se comprueban los usuarios conectados
en una consulta agrupada por barrido (fixedDelay configurable, 1000 ms por defecto).
Antes de publicar a una sucursal se hace otra comprobación agrupada de sus conexiones;
no hay consultas individuales por destinatario ni por frame de salida. Los errores
de acceso a la base cierran las conexiones afectadas. Se explicaron las alternativas
de notificación compartida antes de implementar. El cierre remoto no es instantáneo
y el broker de eventos general continúa siendo local; límites y configuración en
[WEBSOCKETS.md](WEBSOCKETS.md). No se modificaron login ni migraciones.

WebSocketRevocationIntegrationTest agrega siete pruebas con PostgreSQL 18 mediante
Testcontainers, repositorios reales y clientes STOMP contra WebSockets reales.
Cubren múltiples sesiones, rechazo de CONNECT/SUBSCRIBE inactivos, usuario activo
que sigue recibiendo eventos, reactivación, aislamiento, rollback, detección externa
periódica y detección previa a publicación con una consulta SQL para tres conexiones.
La escritura externa confirmada simula otra instancia sin emitir el evento local;
no se levantaron dos procesos del backend. Dos pruebas adicionales del interceptor
comprueban el descarte de eventos encolados y la entrega a usuarios activos.

Validación final: `mvn test`, BUILD SUCCESS, 135 pruebas, cero fallos, errores u
omisiones, en 1 minuto 22 segundos con Java 21 y Maven 3.9.16. El grupo inicial de
WebSockets aprobó 12 pruebas; la suite final incluye además los dos casos de salida.
Log de ejecución local: `target/ws-full-suite.log`.

## Contraseñas e invalidación de credenciales — 7 de octubre de 2026

Se agregaron PATCH `/api/v1/users/me/password` para ADMIN/CASHIER activos y
`/api/v1/users/{userId}/password` para restablecimiento por ADMIN activo de otro
usuario del mismo negocio. Devuelven 204 sin cuerpo. El cambio propio exige
la contraseña actual (400 si es incorrecta); el restablecimiento no la necesita.
Usuarios ajenos/inexistentes devuelven 404 y restablecer la propia cuenta por la
ruta administrativa devuelve 409. Se conserva la inactividad del objetivo.

ValidPassword comparte las reglas anteriores de creación con los dos DTOs nuevos:
no nula/blanca, máximo 72 caracteres y 72 bytes UTF-8. Se reutiliza BCrypt,
conservando espacios significativos, sin exponer ni registrar contraseñas/hashes.
No se implementaron edición de username, displayName o rol.

V32 agrega credential_version persistida y no negativa, inicialmente cero.
AppUser.changePassword incrementa la versión y reemplaza el hash atómicamente.
Los JWT nuevos incluyen esa versión y el filtro HTTP existente la compara en
la misma consulta de identidad. Los tokens antiguos sin claim solo funcionan
mientras la cuenta conserve versión cero. Reactivar no rehabilita tokens de
credenciales anteriores. No se utilizan listas de revocación en memoria.

WebSockets compara versiones en CONNECT, SUBSCRIBE y las comprobaciones agrupadas
periódicas/previas a publicación. UserCredentialsChanged cierra conexiones locales
anteriores AFTER_COMMIT; un rollback no revoca y un evento tardío no cierra sesiones
de una versión nueva. Se conserva el aislamiento y la entrega a usuarios activos.
Para otras instancias se mantienen los límites documentados del barrido y broker local.

Se explicó y corrigió una carrera del login: verificar una contraseña anterior y
recargar después una versión nueva podía emitir un JWT válido con credenciales
viejas. El login ahora bloquea la fila antes de autenticar y hasta el commit.
Los cambios bloquean negocio y usuario; dos cambios propios desde la misma versión
tienen un único ganador, y los restablecimientos concurrentes dejan únicamente
el hash y la versión del último commit. Se conservan formato y errores del login.

UserPasswordIntegrationTest aporta 16 ejecuciones con PostgreSQL 18 de Testcontainers,
JWT reales, MockMvc y repositorios reales. Se comprueban cambios propios, reset,
permisos, aislamiento, inactividad, login anterior/nuevo, tokens revocados/legacy,
validaciones y límites de BCrypt, rollback y concurrencia con transacciones
independientes. La carrera de login verifica bloqueo real con pg_blocking_pids.
WebSocketRevocationIntegrationTest añade tres ejecuciones con sockets reales:
cambio propio, reset y cambio externo confirmado sin evento local. Se verifica
rechazo del JWT anterior, cierre de sesiones, reconexión y recepción por otro usuario.

La primera ejecución del grupo detectó ocho rechazos 403 en la ruta propia por
la regla HTTP general de usuarios exclusiva de ADMIN. Se agregó una excepción
específica para PATCH /me/password, conservando ADMIN para el resto. El grupo
corregido aprobó 49 pruebas, cero fallos/errores/omisiones, con BUILD SUCCESS.
Contrato y estrategia completos: [CONTRASENAS.md](CONTRASENAS.md).

Validación final: `mvn test`, BUILD SUCCESS, **154 pruebas**, cero fallos, errores
u omisiones, en 1 minuto 39 segundos con Java 21 y Maven 3.9.16. Flyway aplicó V32
y Hibernate validó el esquema en PostgreSQL de Testcontainers. No se modificaron
migraciones antiguas ni la base operativa. Log local: `target/password-full-suite.log`.
