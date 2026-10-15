# Revocación de conexiones WebSocket

El endpoint `/ws` utiliza STOMP y el broker simple de Spring en memoria. Los clientes
autentican `CONNECT` con Bearer JWT y `X-Terminal-Id`. Se comprueban usuario, negocio,
terminal y sucursal activos. Solo se permite suscribirse a
`/topic/branches/{branchId}/sales` de la sucursal de su terminal.
Los cambios de cuentas siguen publicándose mediante SaleRealtimePublisher después
del commit. No se cambiaron el formato SaleChangedMessage ni las rutas existentes.

## Desactivación y entrega

UserService publica UserDeactivated dentro de la transacción. Su listener AFTER_COMMIT
invalida todas las conexiones locales del usuario y negocio afectados, incluidos
varios dispositivos, y cierra el transporte con POLICY_VIOLATION (1008). Un rollback
no desconecta usuarios. Se registran administrador, usuario y negocio sin credenciales.

WebSocketSessionRegistry conserva únicamente las conexiones locales y su identidad.
La revocación se marca antes de intentar cerrar el transporte. El canal de salida
comprueba ese registro en memoria antes de encolar y nuevamente antes de entregar al
handler: un evento pendiente se descarta si la sesión fue revocada mientras esperaba.
Una sesión ausente o no autenticada tampoco recibe eventos de negocio. No se consulta
PostgreSQL por cada destinatario ni por cada frame de salida.

SUBSCRIBE vuelve a validar la identidad y la terminal contra PostgreSQL, además del
destino permitido, para rechazar nuevas suscripciones de usuarios inactivos. CONNECT
también mantiene su validación existente. Se rechaza SEND de clientes: actualmente
no existen handlers de mensajes de cliente y los eventos los publica el servidor.
Esto impide inyectar publicaciones directamente al broker.

Al reactivar, se admite una conexión nueva, incluso con un token anterior todavía
vigente. Las conexiones revocadas no se recuperan ni se rehabilitan automáticamente.
La invalidación por cambios de contraseña se detalla en
[CONTRASENAS.md](CONTRASENAS.md): CONNECT, SUBSCRIBE y las comprobaciones agrupadas
comparan también la versión de credenciales. Las conexiones con versiones anteriores
se cierran después del commit local o al detectar el cambio de otra instancia.

## Varias instancias

Cada instancia comprueba en una consulta agrupada los usuarios conectados y sus
negocios activos. La tarea usa fixedDelay de 1000 ms por defecto, configurable con
`app.security.websocket.recheck-interval-ms`. Sin conexiones autenticadas no consulta.
Así detecta también desactivaciones confirmadas por otra instancia, aunque no haya
publicaciones. El intervalo no es un plazo máximo estricto: depende del planificador
y del tiempo de respuesta de PostgreSQL.

Antes de cada publicación de cuentas, un interceptor del broker hace una consulta
agrupada adicional para las conexiones de esa sucursal. Revoca las identidades que
ya estén inactivas en la base antes de distribuir el evento. El número de consultas
no crece con los destinatarios. Ante un error de acceso a la base, se cierran las
sesiones afectadas para evitar entregar datos sin poder validar su estado.

La desconexión local ocurre después del commit; en otras instancias el cierre físico
se produce al detectar el cambio en la comprobación periódica o previa a publicación.
Los frames ya enviados al transporte no pueden retirarse. También existe el límite
normal de concurrencia entre una lectura del estado y un commit posterior en otra
instancia; no se promete orden global instantáneo entre procesos.

Una alternativa para propagar la revocación inmediatamente entre nodos sería un canal
compartido (por ejemplo, Redis o notificaciones PostgreSQL), junto con comprobaciones
de recuperación por mensajes perdidos. Eso requiere infraestructura y gestión de
reconexiones adicionales. Se eligió la comprobación agrupada para este incremento.
El broker simple sigue siendo local: este cambio protege sesiones en varias instancias
con una base compartida, pero no distribuye eventos de cuentas entre nodos. Esa
sincronización general requiere un broker compartido o un bus de eventos aparte.

## Pruebas

WebSocketRevocationIntegrationTest usa PostgreSQL 18 de Testcontainers, repositorios
reales, login real y conexiones STOMP sobre WebSockets contra el servidor HTTP real.
Comprueba varias sesiones de un usuario, cierre tras desactivación, recepción por el
usuario activo, reconexión tras reactivación, rechazo de CONNECT y SUBSCRIBE inactivos,
aislamiento por negocio, rollback y detección de una desactivación externa.

La desactivación externa se simula con un cambio SQL confirmado que no emite el evento
local; no se levantan dos procesos del backend. La comprobación periódica se invoca
explícitamente en esa prueba para evitar depender de tiempos de ejecución. Otra prueba
deshabilita el barrido frecuente y demuestra que la publicación detecta el cambio,
con exactamente una consulta SQL para tres conexiones de la misma sucursal.
WebSocketDeliveryInterceptorTest comprueba también la revocación de un evento encolado.

Comandos: `mvn -Dtest=WebSocketRevocationIntegrationTest,WebSocketSecurityInterceptorTest,WebSocketDeliveryInterceptorTest test`
y `mvn test`. Los resultados de la suite completa se registran en AVANCE.md.
