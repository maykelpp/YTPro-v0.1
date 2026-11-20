# YTPro Control API

API remota exclusiva para estado de la aplicación: activación global, mantenimiento y versión mínima obligatoria. No procesa búsquedas, audio ni descargas.

## Endpoints
- `GET /health`
- `GET /v1/status?versionCode=1`

## Variables de Render
- `APP_ENABLED=true`
- `MAINTENANCE_MODE=false`
- `MIN_SUPPORTED_VERSION_CODE=1`
- `LATEST_VERSION_CODE=1`
- `LATEST_VERSION_NAME=0.1.0`
- `UPDATE_URL=`
- `MAINTENANCE_MESSAGE=...`
- `DISABLED_MESSAGE=...`

Para bloquear la aplicación, cambiar `APP_ENABLED` a `false` o `MAINTENANCE_MODE` a `true`. Para exigir actualización, elevar `MIN_SUPPORTED_VERSION_CODE` por encima de la versión instalada.
