# Probar cuentas abiertas en Postman

Crear variables de entorno:

| Variable | Valor |
| --- | --- |
| baseUrl | http://localhost:8080 |
| accessToken | Token obtenido en login |
| terminalId | ID de una terminal activa |
| cashSessionId | ID de una caja abierta de esa sucursal |
| customerId | ID opcional de cliente activo del mismo negocio |

## Preparación

1. `POST {{baseUrl}}/api/v1/auth/login`, Body → raw → JSON:

```json
{"username":"TU_USUARIO","password":"TU_CONTRASEÑA"}
```

Copiar `accessToken` de la respuesta a la variable del entorno.
Para las solicitudes siguientes usar Authorization → Bearer Token → `{{accessToken}}`.

2. Consultar `GET {{baseUrl}}/api/v1/terminals` y guardar el `id` de la terminal como `terminalId`.
   Si hace falta registrarla: `POST {{baseUrl}}/api/v1/terminals` con
   `{"name":"Tablet Jaibo","branchId":"UUID_DE_LA_SUCURSAL"}`.

3. Consultar `GET {{baseUrl}}/api/v1/cash-sessions/current` con header
   `X-Terminal-Id: {{terminalId}}`. Guardar `id` como `cashSessionId`.
   Si devuelve 204, abrir caja según el flujo existente:
   `POST {{baseUrl}}/api/v1/cash-sessions/open`, mismo header,
   Body JSON `{"openingAmount":500.00}`. Guardar su `id`.

## Abrir una cuenta

`POST {{baseUrl}}/api/v1/sales/open`

Headers:

```text
Authorization: Bearer {{accessToken}}
X-Terminal-Id: {{terminalId}}
Content-Type: application/json
```

Body → raw → JSON:

```json
{
  "cashSessionId": "{{cashSessionId}}",
  "reference": "Familia de la mesa azul"
}
```

Con cliente identificado:

```json
{
  "cashSessionId": "{{cashSessionId}}",
  "reference": "Pedido para llevar",
  "customerId": "{{customerId}}"
}
```

Solo `cashSessionId` es obligatorio. Referencia admite hasta 80 caracteres;
espacios externos se recortan y una referencia vacía se guarda como null.
No enviar usuario, negocio ni consecutivo: los determina el servidor.

Esperado: **201 Created**, `status: "OPEN"`, UUID de cuenta en `id`,
`saleNumber` consecutivo por caja e importes en cero. También devuelve `cashSessionId`,
`branchId`, `reference`, `customerId`, `openedByUserId`, `openedFromTerminalId`,
`createdAt` y `version`. Repetir el POST crea otra cuenta.

## Consultar cuentas abiertas

`GET {{baseUrl}}/api/v1/sales/open`

Enviar Bearer Token y `X-Terminal-Id`. No requiere body.
Esperado: **200 OK**, array de cuentas abiertas de la sucursal de esa terminal,
ordenadas por creación y número. Sin cuentas devuelve `[]`.
Una segunda terminal activa de la misma sucursal consulta las mismas cuentas.
Incluye cuentas que siguen OPEN aunque su caja haya sido cerrada posteriormente.

## Errores previstos

| HTTP | Caso |
| --- | --- |
| 400 | Falta caja o header, UUID inválido o referencia demasiado larga |
| 401 | Sin token válido |
| 403 | Usuario inactivo, negocio inactivo o identidad inconsistente |
| 404 | Caja ajena/inexistente, terminal inválida/inactiva, sucursal inactiva o cliente ajeno/inactivo |
| 409 | Caja cerrada o consecutivo agotado |

El incremento solo abre y consulta cuentas; las respuestas mantienen importes cero hasta
implementar las partidas. No se cambia el estado de las cuentas desde estos endpoints.
