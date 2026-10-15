# Cambio y restablecimiento de contraseñas

Ambas operaciones requieren Bearer JWT y usan el negocio de `business_id` y el
usuario de `user_id`. No requieren terminal. Devuelven **204 sin cuerpo**; nunca
devuelven contraseñas, hashes ni un token nuevo. Después de un cambio propio,
el cliente debe volver al login y abrir una conexión WebSocket nueva.

## Cambio propio

`PATCH /api/v1/users/me/password`

Disponible para ADMIN y CASHIER activos:

```json
{
  "currentPassword": "ClaveActual123!",
  "newPassword": "ClaveNueva123!"
}
```

La contraseña actual se comprueba con PasswordEncoder.matches. Si es incorrecta,
se devuelve 400 sin alterar el hash ni la versión. Un JWT revocado recibe 401.
El estado y la versión se vuelven a comprobar dentro de la transacción tras los
bloqueos, aunque el filtro HTTP ya hubiera admitido la solicitud antes de esperar.

## Restablecimiento administrativo

`PATCH /api/v1/users/{userId}/password`

Solo ADMIN activo, para **otro** usuario de su negocio:

```json
{
  "newPassword": "ClaveNueva123!"
}
```

No requiere la contraseña anterior. Objetivos inexistentes o de otro negocio
devuelven 404, un CASHIER recibe 403 y el restablecimiento administrativo de la
propia cuenta devuelve 409: debe usar `/me/password` con su contraseña actual.
Se permite restablecer un usuario inactivo, pero permanece inactivo y no puede
iniciar sesión ni usar JWT o WebSockets hasta ser reactivado por separado.

## Validaciones y almacenamiento

ValidPassword es la restricción Jakarta compartida por CreateUserRequest,
ChangePasswordRequest y ResetPasswordRequest. Conserva las reglas existentes:
valor no nulo ni vacío/blanco, máximo 72 caracteres y máximo 72 bytes UTF-8.
Los espacios de una contraseña no blanca se conservan exactamente. No se añade
una política de complejidad ni una longitud mínima distinta de la existente.
La contraseña actual también se valida dentro de esos límites.

Se reutiliza el PasswordEncoder BCrypt configurado. No se guardan contraseñas
en claro. Los errores de validación contienen nombres de campos y mensajes,
sin valores rechazados; tampoco se registran contraseñas o hashes en los logs
de estas operaciones. Los DTOs de respuesta de usuarios siguen sin credenciales.

## Invalidación persistente

Flyway **V32** agrega `app_user.credential_version`, BIGINT no nulo, inicialmente
cero y no negativo. AppUser.changePassword reemplaza el hash e incrementa esa
versión dentro de la misma transacción; un rollback revierte ambos cambios.
No se modifican migraciones antiguas ni se crea una lista de tokens en memoria.

El login incluye `credential_version` en el JWT. ActiveUserFilter compara la
versión con la proyección de identidad que ya consulta una vez por solicitud
HTTP protegida. Tokens anteriores al cambio reciben 401, aunque no hayan
expirado. Como transición, un JWT anterior sin ese claim representa la versión
cero: deja de admitirse desde el primer cambio de contraseña. Un claim con tipo
incorrecto o negativo tampoco es válido. La reactivación no reduce la versión
y no rehabilita tokens revocados por un cambio de contraseña.

STOMP CONNECT compara también la versión del JWT con la actual. La identidad
de cada conexión conserva su versión y SUBSCRIBE vuelve a comprobarla. El evento
UserCredentialsChanged se escucha AFTER_COMMIT y cierra las conexiones locales
con versiones anteriores mediante el registro existente. Un evento tardío no
cierra una conexión nueva de la versión vigente. Se conserva el control en
memoria del canal de salida para descartar entregas de sesiones revocadas.

Las comprobaciones agrupadas periódicas y previas a publicar ahora comparan la
versión además de la actividad y el negocio. Detectan cambios confirmados por
otra instancia con PostgreSQL compartido, sin consultas por destinatario. Se
mantienen el intervalo configurable de 1000 ms y los límites de concurrencia y
distribución del broker local documentados en [WEBSOCKETS.md](WEBSOCKETS.md).
El cierre remoto no es instantáneo y los frames ya enviados no pueden retirarse.
Todas las instancias que atiendan HTTP o WebSockets deben ejecutar esta revisión
para aplicar la comparación de versiones; un backend anterior no conoce ese control.

## Concurrencia y login

Los cambios siguen el orden de administración existente: bloqueo pesimista del
negocio y después del usuario objetivo. Dos cambios propios que parten de una
misma versión tienen un solo ganador; el segundo encuentra otra versión y recibe
401. Dos restablecimientos administrativos se serializan: cada commit incrementa
la versión, y únicamente el hash del último commit permite login. Todos los JWT
de versiones anteriores quedan revocados.

Se corrigió una carrera del login: antes verificaba la contraseña y posteriormente
recargaba el usuario. Leer una versión nueva después de verificar una contraseña
vieja podría emitir un token válido indebidamente. Ahora el login bloquea la fila
del usuario antes de autenticar y mantiene el bloqueo hasta el commit de la
emisión. Conserva BCrypt, AuthenticationManager, el formato LoginResponse y los
errores existentes. Un login que espera a un cambio confirmado verifica la nueva
credencial y rechaza la contraseña anterior.

## Verificación

UserPasswordIntegrationTest usa PostgreSQL 18 con Testcontainers, repositorios
reales, JWT firmados y MockMvc. Cubre permisos, aislamiento, ambos roles para
cambio propio, login viejo/nuevo, revocación, inactividad, rollback, tokens sin
claim, límites de caracteres/bytes, ausencia de hashes y concurrencia.
ExecutorService y CountDownLatch coordinan transacciones independientes; la
carrera con login comprueba la espera real con `pg_blocking_pids`.

WebSocketRevocationIntegrationTest añade pruebas con clientes STOMP y WebSockets
reales para cambio propio, restablecimiento, rechazo de JWT anterior, reconexión
y recepción por usuarios activos. Simula un cambio de otra instancia mediante
SQL confirmado sin evento local y comprueba SUBSCRIBE y publicación. No se
levantan dos procesos del backend en esas pruebas.

Comando final: `mvn test`. Resultado registrado en [AVANCE.md](AVANCE.md).
Quedan fuera edición de username, displayName o rol.
