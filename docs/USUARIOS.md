# Administración de usuarios — primer incremento

Ambos endpoints requieren `Authorization: Bearer <accessToken>` de un ADMIN activo.
El negocio se obtiene de `business_id` y la identidad de `user_id` del JWT.
Además del rol del token, se comprueban rol, actividad y pertenencia en la base de datos.
No requieren `X-Terminal-Id`.

## Listado

`GET /api/v1/users`

Devuelve 200 y un array de usuarios del negocio autenticado, ordenados por username.
Incluye activos e inactivos. Campos: `id`, `username`, `displayName`, `role`, `active`.
No incluye contraseñas ni hashes.

## Registro

`POST /api/v1/users`, `Content-Type: application/json`:

```json
{
  "username": "cajero.tablet",
  "password": "ClaveDelUsuario123!",
  "displayName": "Cajero de tablet",
  "role": "CASHIER"
}
```

Devuelve 201 y los mismos campos del listado. También se admite `ADMIN`.
Los nuevos usuarios se crean activos; no se reciben negocio, hash ni estado en el DTO.
El nombre de usuario se recorta y convierte a minúsculas como en AppUser.
El nombre visible se recorta. La contraseña conserva exactamente el valor recibido,
incluidos espacios, y se almacena mediante el PasswordEncoder BCrypt existente.

Validaciones: username no vacío y hasta 60 caracteres; displayName no vacío y hasta 120;
contraseña no vacía, máximo 72 caracteres y 72 bytes UTF-8 por el límite de BCrypt;
rol obligatorio ADMIN o CASHIER.

## Compatibilidad con el login

El identificador actual de inicio de sesión es username. El login no recibe negocio y
V1 contiene un índice único global sobre LOWER(username). Se conserva esa unicidad:
un identificador ya utilizado no puede registrarse ni en el mismo negocio ni en otro.
Se comprueba antes de crear y se trata la violación del índice por registros concurrentes.
No se modificaron AuthService, DatabaseUserDetailsService, el PasswordEncoder, AppUser ni migraciones.

Se deshabilitó la exposición automática de AppUserRepository en Spring Data REST para
evitar acceso directo a entidades/hashes y escrituras fuera de los endpoints autorizados.

## Respuestas de error

| Estado | Caso |
| --- | --- |
| 400 | Campos inválidos, rol desconocido, JSON ilegible o contraseña mayor al límite de BCrypt |
| 401 | Sin token válido, usuario/negocio inactivo o identidad inconsistente |
| 403 | Usuario activo con rol distinto de ADMIN |
| 409 | Username duplicado, incluidas diferencias de mayúsculas y registros concurrentes |

Las pruebas detectaron que GlobalExceptionHandler convertía en 500 las denegaciones
de seguridad, cuerpos ilegibles y rutas inexistentes. Se agregaron manejadores específicos
para 403, 400 y 404, sin devolver diagnósticos que puedan contener la contraseña.

## Verificación

`mvn -Dtest=UserControllerIntegrationTest test`

Las pruebas usan PostgreSQL 18 con Testcontainers y repositorios reales. Cubren ambos
roles al crear, BCrypt y login real, aislamiento y restricciones de acceso, duplicados
globales/concurrentes, usuarios inactivos, validación y bloqueo de la exposición Data REST.

En el primer incremento quedaron fuera edición, desactivación y cambio de contraseñas.

Resultado del 6 de octubre de 2026: `mvn test` terminó con BUILD SUCCESS;
117 pruebas, cero fallos, errores u omisiones. Incluye 23 ejecuciones nuevas de usuarios
y todas las pruebas existentes de login, caja, compras, ventas y comunicación en tiempo real.

## Activación y desactivación

`PATCH /api/v1/users/{userId}/activate`

`PATCH /api/v1/users/{userId}/deactivate`

Requieren Bearer Token de ADMIN activo y no requieren body ni terminal.
Devuelven 200 y el mismo UserResponse del registro, con el estado actualizado.
El objetivo debe pertenecer al negocio del JWT; objetivos ajenos o inexistentes devuelven 404.
Un administrador no puede desactivarse a sí mismo (409), ni puede desactivarse al último
administrador activo (409). Repetir una operación devuelve el estado esperado sin borrar
el usuario, cambiar contraseña ni modificar sus registros históricos.

Las operaciones son transaccionales. Se bloquea pesimistamente el negocio antes de
cargar al administrador y al objetivo. Se comprueban los permisos nuevamente tras esperar
el bloqueo, y el conteo de administradores se realiza dentro de la misma transacción.
Esto serializa las modificaciones de estado de un negocio y evita que dos administradores
se desactiven mutuamente. Con la prohibición de desactivar la propia cuenta, el último
administrador ya queda protegido también por esa regla; el conteo es una defensa adicional.
El bloqueo interno no se expone mediante Spring Data REST.

Se registran administratorId, userId, businessId, estado y si hubo cambio, incluidos
los intentos idempotentes exitosos. No se registran contraseñas ni tokens.

## Validación central de identidad HTTP

Se confirmó que antes las rutas que confiaban únicamente en el JWT seguían permitiendo
acceso a usuarios desactivados. ActiveUserFilter ahora comprueba la identidad en la base
después de la validación criptográfica y antes de autorizar la ruta protegida.
Usuarios inexistentes, inactivos o de un negocio inconsistente/inactivo reciben 401,
incluso con un JWT emitido previamente que todavía no expire. El rol efectivo procede
del registro actual del usuario. El principal sigue siendo Jwt para compatibilidad.

Se usa una sola consulta de proyección de usuario/negocio por solicitud autenticada.
La identidad se conserva únicamente durante esa solicitud, sin caché entre solicitudes.
Listado y registro reutilizan esa validación; las modificaciones de estado siempre
vuelven a comprobar permisos en la transacción después de bloquear, porque la identidad
puede haber cambiado mientras esperaban. Las llamadas directas al servicio también validan.

Login POST, health, info y el handshake /ws conservan su acceso público y no pasan por
esta comprobación de estado. No se cambió el decoder ni el flujo de login. WebSockets
conserva su validación propia de identidad/terminal en STOMP CONNECT; sus pruebas siguen
vigentes. La revocación de conexiones WebSocket existentes se implementó mediante
un registro y controles propios de STOMP, separados de este filtro HTTP; véase
[WEBSOCKETS.md](WEBSOCKETS.md).

La reactivación vuelve a permitir el login. No hay una lista permanente de tokens revocados:
un token previo aún vigente vuelve a ser admisible cuando se reactiva el usuario.
Esto implementa el control de actividad solicitado; no un cambio de contraseña ni de sesiones.

Pruebas de este incremento: UserActivationIntegrationTest, con PostgreSQL de Testcontainers,
JWT reales emitidos por login, transacciones independientes y ExecutorService/CountDownLatch.
Se verifica desactivar/reactivar, historial, JWT previo, permisos, auto-desactivación,
último administrador, aislamiento, concurrencia, idempotencia, rutas públicas y consultas SQL.
El resultado final de `mvn test` se registra en AVANCE.md.

## Contraseñas

Se agregaron `PATCH /api/v1/users/me/password` para ADMIN/CASHIER activos y
`PATCH /api/v1/users/{userId}/password` para restablecimiento por otro ADMIN
del mismo negocio. Ambos devuelven 204 sin credenciales. El cambio incrementa
una versión persistida que invalida JWT y sesiones WebSocket anteriores.
Contrato, validaciones, concurrencia e inactividad: [CONTRASENAS.md](CONTRASENAS.md).
