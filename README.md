# YTPro v0.1

Aplicación Android de música con búsqueda, vista previa por streaming y descargas locales. Inspiración visual: YouTube Music y Mornye.

## Componentes
- `app/`: cliente Android nativo.
- `control_api/`: API en Render para activación, mantenimiento y control de versiones.
- Panel privado de administración: `/admin`.

La búsqueda, reproducción y descarga se ejecutan en el dispositivo. Render no transporta audio ni archivos multimedia.

## Configurar el panel de Render

1. En el servicio `ytpro-control-api`, abre **Environment** y agrega:
   - `ADMIN_USERNAME`: nombre de usuario para el panel.
   - `ADMIN_PASSWORD`: contraseña larga y única. No la guardes en GitHub ni dentro de la app.
2. En **Disks**, agrega un disco persistente:
   - Mount path: `/var/data`
   - Size: el mínimo permitido por tu plan.
3. Confirma que el servicio usa `control_api` como directorio de trabajo y que inicia con:
   `uvicorn app:app --host 0.0.0.0 --port $PORT`
4. Espera a que termine el despliegue y abre:
   `https://ytpro-control-api.onrender.com/admin`
5. El navegador solicitará el usuario y la contraseña configurados en el paso 1.

**Importante:** el panel guarda la configuración en `/var/data/ytpro_config.json`. El disco persistente es necesario para conservar los cambios después de reinicios o nuevos despliegues. Sin el disco, el guardado devolverá un error y no afirmará que los cambios se guardaron.

Rutas públicas:
- `/health`: comprobación de salud.
- `/v1/status?versionCode=1`: estado que consulta la app.
- `/docs`: documentación técnica de la API.

El panel usa autenticación HTTP Basic sobre HTTPS. No compartas las credenciales y no habilites CORS público para operaciones administrativas.
