# Validación de V3Integracion1.0

Fecha: 29 de octubre de 2026.

## Compilación

Se generó correctamente `app/build/outputs/apk/debug/app-debug.apk` (80 286 117 bytes en esta compilación). Incluye las bibliotecas sound-engine y audioToHaptics, los dos modelos TFLite cotidianos y Basic Pitch ONNX. No incluye Demucs.

La aplicación compila con las advertencias heredadas de opciones Gradle y APIs Android deprecadas. No hay errores de compilación.

## Pruebas ejecutadas

| Grupo | Superadas | Omitidas | Fallos |
|---|---:|---:|---:|
| Aplicación, JVM | 11 | 0 | 0 |
| Biblioteca musical, JVM | 25 | 6 | 0 |
| Aplicación instalada en emulador Android 17/API 37 | 7 | 0 | 0 |
| Total | 43 | 6 | 0 |

Resultados por suite: `resultado_pruebas.json`. Informes completos en `app/build/reports/tests/`, `audioToHaptics/build/reports/tests/` y `app/build/reports/androidTests/connected/`.

### Integración comprobada en emulador

- Carga de contexts.json y clases.txt del equipo: cuatro contextos y siete etiquetas entrenadas.
- Casos disponibles por contexto coinciden con los archivos originales. Timbre se identifica como pendiente, no como detección disponible.
- YAMNet real ejecuta inferencia y entrega embeddings de 1024 dimensiones; head.tflite real entrega siete probabilidades finitas cuya suma es uno. Esta prueba usa silencio y comprueba compatibilidad e inferencia, no precisión de categorías.
- Dos sesiones consecutivas de SoundEngine abren el micrófono y liberan la captura y los modelos con releaseAndJoin.
- La biblioteca musical detecta Basic Pitch presente y Demucs ausente. Invocar su API con configuración de prueba produce el error original ModelNotAvailable antes de procesar un archivo; no se genera ni descarga un modelo.
- La interfaz permite navegar por Hogar, Social, Industrial y Urbano y muestra los casos correspondientes; el juego musical no aparece.
- El botón Analizar música está deshabilitado cuando falta Demucs y se muestra el motivo.
- Se conserva la prueba existente de decodificación de audio de la app.

Capturas generadas durante la prueba de interfaz y revisadas visualmente: `capturas/inicio.png`, `capturas/musica_modelos.png`, `capturas/cotidiano_hogar.png`, `capturas/cotidiano_industrial.png`.

### Pruebas musicales omitidas

No están presentes los recursos externos requeridos por seis pruebas: referencia numpy del remuestreador, stems y referencias de Basic Pitch, Demucs y su referencia, batería real y las dos canciones del pipeline completo. Estas pruebas constan como omitidas, no como superadas.

La prueba de comparación Basic Pitch originalmente falló porque todos sus datos faltaban y el informe quedaba vacío. Se añadió una condición de disponibilidad de referencias, documentada en CAMBIOS_INTEGRACION.md. No se modificaron el modelo ni las comparaciones cuando existen datos.

## Conservación de fuentes

`scripts/verificar_fuentes.py` comparó 47 archivos de los tres módulos contra los proyectos extraídos: 43 idénticos y cuatro modificados. Los cambios son dos scripts Gradle, la adición de cierre seguro de SoundEngine y la condición de disponibilidad de una prueba. No hay otros cambios en código de producción de los motores. Las huellas, el resumen y los diffs están en `auditoria/`.

## Lo que no se ha validado

- No hay prueba nueva de precisión acústica con un conjunto etiquetado ni medición fiable de distancia.
- No se ha conectado un brazalete físico: conexión, compatibilidad real del firmware, latencia y vibración requieren ese equipo.
- No se ha ejecutado el análisis musical completo en esta aplicación porque falta Demucs; las pruebas DSP y síntesis sí se ejecutaron con datos sintéticos.
- No se transmiten tres canales musicales al ESP32. La vista previa de resultados es escucha de archivos WAV.
- No se confirmaron parámetros de actuadores ni consumo de memoria de la separación en un teléfono real.

## Repetir

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :audioToHaptics:testDebugUnitTest :app:connectedDebugAndroidTest
python scripts/verificar_fuentes.py
```

La última orden necesita las carpetas originales Audio y Cotidiano junto a V3Integracion1.0. Las pruebas conectadas necesitan un emulador o teléfono Android con depuración habilitada.
