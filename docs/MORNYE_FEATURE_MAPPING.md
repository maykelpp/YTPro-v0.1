# YTPro v0.1 — estado de implementación y validación

Este documento diferencia lo que está conectado a la aplicación, lo que existe como módulo y lo que sigue pendiente. El análisis de Mornye se utilizó únicamente como referencia funcional; no se copiaron su código, marca ni recursos.

## Funciones conectadas a la interfaz

- Biblioteca local: permiso de lectura, exploración MediaStore, listado y reproducción de archivos locales. La reproducción usa transición fade de salida/entrada al cambiar de pista.
- Listas locales: creación y persistencia básica de una lista con las pistas escaneadas.
- Efectos de audio: pantalla de ecualizador, graves y ganancia; el soporte real depende de AudioFX y del dispositivo.
- Cola de descargas: añadir canciones, procesarlas secuencialmente, mostrar estado y mensaje de error, limpiar tareas terminadas.
- Letras LRC: importar un archivo .LRC y resaltar la línea activa según la posición del reproductor.
- Búsqueda y vista previa: se mantiene yt-dlp; se preparan enlaces de las primeras ocho coincidencias en segundo plano. Si no hay enlace en caché, la resolución de la vista previa tiene un límite de espera de 2 segundos y muestra error si lo supera.

## Módulos que aún no están conectados a la interfaz principal

- Editor de metadatos MediaStore: módulo disponible, falta flujo de edición y autorización de escritura.
- MediaSessionService: servicio declarado, pero el ExoPlayer de la pantalla principal todavía no está conectado a esa sesión.
- Cola e historial persistentes: los estados de la cola viven en memoria; no hay historial durable ni porcentaje de progreso.

## Funciones no implementadas en esta fase

- AutoMix con análisis BPM y mezcla beat-matched de dos reproductores.
- Cast, NAS, Discord, Shazam y widgets.
- Descarga automática de letras y proveedores externos.
- Pruebas físicas en todos los dispositivos Android 10–16 y con equipos de audio externos.

## Pruebas automatizadas añadidas

- 20 casos unitarios para el parser y la selección de línea LRC.
- 20 casos unitarios para la cola de descargas (éxito, error, orden, mensajes y limpieza).
- El workflow debe ejecutar `testDebugUnitTest` antes de `assembleDebug`.

**Importante:** los casos unitarios no sustituyen las pruebas de red, reproducción, permisos ni interacción en un teléfono real. La meta del 70 % es un objetivo de aceptación, no un resultado que se pueda afirmar antes de ejecutar y revisar todas las pruebas.
