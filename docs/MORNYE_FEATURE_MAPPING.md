# YTPro v0.1 — módulos inspirados en el análisis estático de Mornye 5.4.0

Este documento separa evidencias del APK analizado de la implementación original de YTPro.

## Evidencias observadas en el análisis
- Biblioteca local con exploración por carpetas, álbumes, artistas y listas.
- Reproductor interno, vista previa y reproductor compacto.
- Cola, historial y recuperación de descargas.
- Ecualizador, refuerzo de graves/agudos y normalización.
- AutoMix, salida USB y reproducción bit-perfect (dependiente de hardware).
- Letras y metadatos.
- Integraciones mencionadas en cadenas: Shazam, Discord, Google Cast y NAS.

Las cadenas extraídas no prueban que cada función esté activa ni describen por completo su comportamiento.

## Base implementada en esta fase
- Consulta de audio local mediante MediaStore.
- Persistencia local de listas de reproducción.
- Control de ecualizador, graves y loudness mediante Android AudioFX.
- Parser LRC con búsqueda binaria de la línea activa.
- Cola serial de descargas con estados y errores.
- Edición de metadatos MediaStore (sujeta a permisos del archivo).
- Transición fade-out/fade-in de un solo reproductor.
- Servicio Media3 MediaSession para preparar reproducción en segundo plano.

## Pendiente de integración y validación
- Conectar la UI principal a MediaSessionService.
- Persistir cola e historial de descargas y reportar porcentaje real.
- Escaneo de BPM y mezcla beat-matched con dos decks.
- Búsqueda y descarga de letras con proveedores configurables.
- Widgets de pantalla de inicio, Cast, NAS, Discord y Shazam.
- Pruebas físicas de Android 10–16 y hardware de audio externo.

No se reutiliza código, marca ni recursos privados de Mornye.
