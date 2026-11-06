# Integración de los algoritmos en PrototipoV3

Fecha: 29 de octubre de 2026. Análisis del código local y de los resultados incluidos en los repositorios. Las mediciones de los proyectos originales no equivalen a pruebas nuevas en esta aplicación ni en el brazalete.

## Resultado

La integración es viable. Cotidiano necesita un adaptador de clasificación y reglas; música necesita incorporar su biblioteca Android y ampliar la reproducción y la salida háptica. Copiar modelos y activar un botón no completa ninguna de las dos integraciones.

| Parte | Estado actual de PrototipoV3 | Qué aporta el proyecto recibido |
|---|---|---|
| Cotidiano | `AlgoritmoEntornoYamnet` reconoce categorías generales de YAMNet, con perfiles social y vial; envía comandos al ESP32 | `sound-recognition` tiene YAMNet + clasificador propio de siete clases, cuatro contextos y reglas por clase |
| Música | Reproduce archivos, pero `RegistroAlgoritmos.crearMusica()` devuelve `AlgoritmoMusicaPendiente` | `AudioToHaptics` separa instrumentos, transcribe y genera tres señales sincronizadas |
| Bluetooth | `ConexionEntornoBle` comprueba el servicio y la característica antes de marcar conectado | Cotidiano puede reutilizarlo; música requiere ampliar protocolo y firmware |

Los cambios de esta versión retiran el juego musical y agregan en Inicio estados separados del adaptador Bluetooth y de la conexión GATT háptica. Eso no integra todavía los algoritmos recibidos.

## 1. Algoritmo cotidiano

Origen: `../Cotidiano/sound-recognition-main/sound-recognition-main/`.

### Qué hace

Procesa audio mono a 16 kHz en ventanas de 15 600 muestras (0,975 s), con avance de 7 800 (0,4875 s). YAMNet convierte la ventana en un vector de 1 024 características. `head.tflite` clasifica ese vector como claxon, fondo, ladrido, llanto de bebé, maquinaria, sirena o vidrio roto. El entrenamiento Python usa una capa de 256 neuronas y salida softmax, con particiones de entrenamiento/validación/prueba por folds.

`contexts.json` define hogar, urbano, social e industrial, con umbral y prioridad por clase. El motor exige dos ventanas consecutivas con la misma detección válida y limita las repeticiones a una por clase cada tres segundos. Calcula un nivel acústico estimado, pero el ajuste por teléfono está pendiente; no permite afirmar distancia física fiable.

### Qué existe y qué limita su uso

- Están presentes los modelos TFLite y las siete etiquetas, tanto para desarrollo como en los assets Android.
- Alarma de humo, timbre y nombre de usuario aparecen en la configuración, pero no son clases del clasificador entrenado. La configuración no añade esa capacidad.
- El README describe un prototipo entrenado con pocos datos. No hay base en esta revisión para prometer un porcentaje de precisión en el uso real.
- El modelo propio no incluye alarma; sustituir al YAMNet actual sin conservar una ruta de respaldo quitaría esa detección de la app.

### Cómo montarlo en V3

1. Crear `AlgoritmoCotidianoEquipo : AlgoritmoCotidiano`, conservando la captura única de `EntradaAudioAndroid`. Extraer la inferencia y las reglas de `SoundEngine`; no iniciar su `AudioRecord`, porque la app ya administra el micrófono.
2. Guardar sus recursos en `assets/cotidiano_equipo/`. Los dos proyectos contienen `yamnet.tflite`, pero la app actual espera una sola salida de categorías; el motor recibido busca una salida de embeddings de 1 024 componentes. No sobrescribir el archivo existente ni asumir que son intercambiables.
3. Cargar juntos `yamnet.tflite`, `head.tflite`, `clases.txt` y `contexts.json`; comprobar dimensiones y orden de etiquetas.
4. Acumular los bloques de audio hasta completar ventanas y conservar el solapamiento. Implementar reinicio, cancelación y cierre de ambos intérpretes.
5. Mapear `ladrido → PERRO`, `llanto_bebe → BEBE`, `sirena → SIRENA`, `claxon → BOCINA`. Para maquinaria y vidrio roto hay que ampliar `SonidoCotidiano`, la interfaz, los patrones y el firmware, o mostrar la detección sin transmitir un comando no soportado.
6. Definir la relación entre los cuatro contextos recibidos y los perfiles SOCIAL/VIAL de V3: son conceptos diferentes. El perfil actual también selecciona el firmware esperado.
7. Conservar las reglas por clase. `IntegracionViewModel` aplica hoy un umbral global de 0,35 y una separación global de tres segundos: debe decidirse dónde queda cada filtro para evitar reglas duplicadas o pérdida de alertas prioritarias.
8. Seleccionar el adaptador en `RegistroAlgoritmos.crearCotidiano`. Reutilizar `ConexionEntornoBle` y sus comandos existentes. Mantener el clasificador actual como alternativa explícita mientras se compara su desempeño.

No añadir otra dependencia TensorFlow Lite sin revisar la ya instalada: V3 utiliza LiteRT con la API `org.tensorflow.lite.Interpreter`.

## 2. Algoritmo musical

Origen: `../Audio/Tesis-main/Tesis-main/`. Tiene un prototipo Python en `Practica/` y una implementación Android reutilizable en `AudioToHaptics/`.

### Qué hace

La biblioteca decodifica un archivo local, remuestrea a 44,1 kHz y separa fuentes con HTDemucs de seis fuentes. Usa voz, guitarra y batería. Basic Pitch transcribe voz y guitarra; el detector de flujo espectral obtiene los golpes de batería. Después sintetiza tres WAV mono PCM16 sincronizados, junto con MIDI, eventos CSV y un manifiesto de validación.

La vibración representa la duración, el ritmo y opcionalmente la intensidad de los eventos. Cada canal usa una portadora fija; no codifica la altura de cada nota como frecuencia de vibración.

### Bloqueos y diferencias importantes

- En la extracción existe `basic_pitch_nmp.onnx` (230 444 bytes), pero no `htdemucs_6s.onnx`. El módulo de modelos exige ese archivo y comprueba su SHA-256; sin él no se completa la separación musical.
- La API real es `AudioToHaptics.process(uri, ...)`, para una canción completa. El contrato actual de V3, `AlgoritmoMusica.procesar(BloqueAudio)`, recibe fragmentos mono; no es un reemplazo directo y puede perder la información estéreo necesaria para la separación.
- `EventoMusical` no contiene un canal de actuador. `SecuenciadorMusical.avanzar()` devuelve solo el evento más reciente si coinciden varios: perdería simultaneidad entre voz, guitarra y percusión.
- La salida musical actual llama a `SalidaHapticaTelefono`; no envía música al ESP32.
- Los informes originales registran aproximadamente 2,76 GB de memoria pico en emulador y procesamiento que puede superar la duración de la canción. Son resultados documentados del proyecto original; falta medir el teléfono real.
- Los valores oficiales de portadoras y muestreo están pendientes. `HapticsConfig.default()` rechaza esa configuración; `testValues()` usa valores de laboratorio y los marca sin confirmar.

### Cómo montarlo en V3

1. Obtener o regenerar `htdemucs_6s.onnx` siguiendo `AudioToHaptics/docs/MODELOS.md` y verificar la huella requerida.
2. Reutilizar `audioToHaptics` y `audioToHapticsModels`, como módulos o AAR. Si se usan AAR locales, declarar ONNX Runtime y las dependencias transitivas indicadas por su guía.
3. Compatibilizar el proyecto: V3 tiene minSdk 24 y destino Java 11; la biblioteca requiere minSdk 26 y Java/Kotlin 17. Para integrar módulos también hay que adaptar sus plugins de Kotlin a la configuración AGP 9 de V3, en lugar de copiar sus scripts Gradle sin cambios.
4. Crear un servicio de análisis por URI, separado del contrato de bloques: `analizarCancion(uri): ProcessingResult`. Llamarlo desde `IntegracionViewModel.analizar`, con progreso, cancelación, exclusión entre análisis y manejo de memoria/modelos ausentes. La biblioteca ya decodifica; no pasar la canción por los bloques mono actuales.
5. Guardar resultados por canción para reutilizarlos. Al elegir otro archivo o iniciar un nuevo análisis, invalidar la línea de tiempo anterior.
6. Ampliar el modelo de reproducción para conservar canal, inicio, duración, intensidad y eventos simultáneos. Usar la posición real del reproductor para pausa, búsqueda y repetición; definir cómo reanudar eventos que ya estaban activos al buscar.
7. Para una primera demostración en el teléfono, mezclar las envolventes de los tres canales con una regla explícita. Un solo vibrador no representa tres actuadores independientes ni reproduce necesariamente las portadoras de los WAV.
8. Para tres canales físicos, acordar el protocolo con hardware: identificador de actuador, tiempo, duración, amplitud, búfer y confirmaciones. Enviar eventos o envolventes compactas requiere soporte nuevo del firmware; no enviar WAV directamente al protocolo actual.

## 3. Comunicación con la háptica

El código y los firmware incluidos usan `VIBRAFIL_ESP32`, servicio `12345678-1234-1234-1234-1234567890ab` y característica de comandos `abcdefab-1234-5678-1234-abcdefabcdef`.

Social admite `DOG`, `BABY`, `NAME`; vial admite `SIREN`, `HORN`, `ALARM`. También existe `STOP`. Los dos firmware comparten identificadores, por lo que una conexión GATT válida no confirma qué conjunto de órdenes acepta el firmware instalado.

`especificacion_protocolo_ble.md` describe otro nombre, otros UUID y JSON para notas. No coincide con la implementación incluida y debe reconciliarse antes de usarlo como contrato de integración.

El indicador Conectada confirma conexión y descubrimiento del servicio de comandos; no verifica que el motor esté vibrando. La confirmación de escritura GATT tampoco equivale a una medición física. Conviene añadir identificación de firmware/capacidades y confirmación de ejecución para la futura integración musical.

## 4. Orden de implementación propuesto

1. Comprobar la aplicación base y los estados Bluetooth en un teléfono: permisos aceptados/denegados, activación externa, desconexión, firmware incompatible y regreso a Inicio.
2. Integrar el clasificador cotidiano como alternativa seleccionable; comparar ambos modelos con audios etiquetados, ruido y silencios. Medir falsos positivos, omisiones y tiempo hasta la alerta.
3. Conseguir Demucs e integrar el análisis musical por URI, primero sin transmisión. Comparar los eventos y WAV con los resultados del proyecto original.
4. Medir memoria, tiempo y cancelación con canciones cortas y largas en el teléfono real. Mantener separadas la escucha ambiental y la reproducción musical para evitar reconocer la música emitida por la propia app.
5. Definir canales y protocolo del hardware; después integrar y medir sincronización, latencia, parada y respuesta física.

La integración de estos algoritmos queda propuesta en este documento; no está implementada por este análisis.
