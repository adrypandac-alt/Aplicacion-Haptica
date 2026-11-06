# Cómo funciona el prototipo Android

El proyecto tiene dos modos. **Cotidiano** escucha el micrófono, identifica ciertos sonidos y los comunica con una tarjeta y, cuando existe un patrón, con una vibración. **Música** abre un archivo local y reúne las herramientas para convertir voz, guitarra y percusión en señales hápticas sincronizadas.

## 1. Cómo se genera un aviso

1. [SoundEngine.kt](../Codigo/sound-engine/src/main/java/com/indoamerica/haptica/soundengine/SoundEngine.kt) toma audio mono a 16 kHz. YAMNet convierte una ventana de casi un segundo en 1024 valores que describen el sonido. El modelo `head.tflite` usa esos valores para calcular la probabilidad de cada clase.
2. El motor toma la clase con mayor probabilidad. Consulta el contexto y verifica la confianza mínima y el nivel estimado de sonido. Emite un evento si la clase se confirma en dos ventanas consecutivas y han pasado más de tres segundos desde el último aviso de esa clase.
3. [IntegracionViewModel.kt](../Codigo/app/src/main/java/com/rommelguerra/prototipomusichaptic/integracion/IntegracionViewModel.kt) recibe el evento, actualiza la última detección y el historial, y activa la salida háptica disponible. El dispositivo ESP32 recibe un comando si está conectado y el perfil admite esa señal. Para los patrones disponibles, el teléfono sirve como salida alternativa.
4. [PanelesEquipos.kt](../Codigo/app/src/main/java/com/rommelguerra/prototipomusichaptic/ui/screens/PanelesEquipos.kt) usa el nombre de la clase para elegir el color y el icono. También muestra la confianza, la prioridad y el contexto del evento.

| Sonido | Contexto | Aviso visual |
|---|---|---|
| Ladrido de perro | Hogar | Ámbar y huella |
| Llanto de bebé | Hogar | Violeta y rostro de bebé |
| Vidrio roto | Hogar | Azul y triángulo de advertencia |
| Sirena de emergencia | Urbano, Social e Industrial | Rojo y altavoz |
| Claxon | Urbano | Verde y automóvil |
| Maquinaria | Industrial | Gris azulado y brazo industrial |

«Sirena» es la categoría que entrega el modelo. Una ambulancia es una posible fuente de ese sonido. En la lista de sonidos de cada contexto puedes probar la tarjeta manualmente. La demostración aparece junto al botón y queda fuera del historial. Vidrio roto y maquinaria tienen aviso visual.

## 2. Cómo se unieron los dos algoritmos

[settings.gradle.kts](../Codigo/settings.gradle.kts) incorpora `sound-engine` y `audioToHaptics` como módulos. [app/build.gradle.kts](../Codigo/app/build.gradle.kts) declara ambos como dependencias de la aplicación. [RegistroAlgoritmos.kt](../Codigo/app/src/main/java/com/rommelguerra/prototipomusichaptic/integracion/RegistroAlgoritmos.kt) crea los motores originales y `IntegracionViewModel` coordina cuándo se ejecuta cada uno.

| Modo | Entrada | Conexión con la app | Resultado |
|---|---|---|---|
| Cotidiano | Micrófono | `SoundEngine.events.collect` entrega eventos al ViewModel | Tarjeta, historial y señal háptica cuando existe un patrón |
| Música | `Uri` del archivo elegido | `AudioToHaptics.process(uri)` entrega progreso y resultado al ViewModel | WAV por canal, MIDI, eventos y manifiesto |

Este fragmento resume la parte del ViewModel que recibe una detección.

```kotlin
next.events.collect { e ->
    if (version != versionEscucha) return@collect
    ultimaDeteccion = e
    // Aquí también se actualiza el historial.
    emitir(e.clase)
}
```

La comprobación de versión descarta eventos de sesiones anteriores. `ultimaDeteccion = e` guarda el evento completo. Compose observa ese estado y redibuja el aviso con los datos nuevos. La llamada `emitir(e.clase)` usa la misma clase para resolver la salida háptica.

En Música, la biblioteca decodifica el archivo y usa **Demucs** para separar las fuentes. **Basic Pitch** obtiene notas de voz y guitarra, y un detector de ataques identifica golpes de percusión. Con esos eventos se generan los tres canales de vibración. La app detiene la escucha del entorno al iniciar el análisis y bloquea su arranque mientras el análisis está activo. Los dos motores comparten la aplicación y su coordinación. Cada uno procesa su propia entrada.

La compilación actual incluye Basic Pitch y permite reproducir archivos de música. Para activar la separación completa se necesita incorporar el archivo validado `htdemucs_6s.onnx` y recompilar.

## 3. Cuánto pesa en el teléfono

| Concepto | Tamaño |
|---|---:|
| APK actual de depuración, para varias arquitecturas Android, sin Demucs | **80,5 MB** |
| YAMNet y clasificador de sonidos juntos, como archivos de modelo | 17,2 MB |
| APK estimada con Demucs incluido sin comprimir | **195 MB** |
| Copia interna de Demucs al primer análisis | **115 MB adicionales**, aproximadamente |

La APK actual ocupa 80 504 803 bytes. El cálculo con Demucs suma los 114,6 MB que reporta el equipo de audio para ese archivo. La ocupación total después de instalar depende de las bibliotecas que Android extrae para el dispositivo y de los archivos generados al analizar canciones.

Fuente del tamaño de Demucs: `AudioToHaptics/docs/INFORME_VIABILIDAD.md` en las fuentes del equipo de audio.
