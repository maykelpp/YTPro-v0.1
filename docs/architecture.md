# Arquitectura YTPro v0.1

## Android
- Kotlin + Jetpack Compose para interfaz.
- Media3 ExoPlayer para streaming de vista previa.
- Las descargas y biblioteca se guardan en el dispositivo.
- La pantalla principal queda bloqueada hasta validar estado remoto.

## Render
Render aloja únicamente `control_api/`: activación global, mantenimiento y versión mínima obligatoria. No se utiliza como proxy de audio ni como motor de descargas.

## Pendiente de la siguiente fase
- Integrar yt-dlp dentro del dispositivo Android y definir el adaptador de búsqueda/extracción.
- Conectar resultados reales con ExoPlayer.
- Implementar cola, descargas, notificaciones y biblioteca local.
- Generar el icono raster definitivo y probar instalación en Android 10–16.
