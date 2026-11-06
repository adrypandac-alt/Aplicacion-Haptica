# Registro de cambios de V3Integracion1.0

Fecha: 29 de octubre de 2026.

## Alcance y procedencia

Se creó una copia de PrototipoV3 en V3/V3Integracion1.0. Los módulos se copiaron desde las carpetas Audio y Cotidiano; estas fuentes y PrototipoV3 no se editaron durante esta integración. No se entrenaron modelos, no se regeneró Demucs y no se modificaron los firmware.

Se mantienen pesos, clases, contextos, umbrales, prioridades, confirmación temporal, antirrebote, extracción de características, separación, transcripción, detección de percusión y síntesis de los proyectos recibidos. Hay una adición de gestión de recursos al motor cotidiano y un ajuste de una prueba musical, descritos abajo.

## 1. Cambios en el código del equipo cotidiano

**Archivo:** `sound-engine/src/main/java/com/indoamerica/haptica/soundengine/SoundEngine.kt`.

**Sección:** nuevo método público suspendido `releaseAndJoin`, junto a `stop` y `release`.

**Qué cambió:** se añadió un método que cancela la tarea de captura, espera a que finalice mediante `cancelAndJoin`, cancela su scope y cierra los dos intérpretes. Los métodos originales permanecen disponibles. La app integrada utiliza este nuevo cierre sin llamar antes a `stop`, de modo que conserva la referencia de la tarea hasta terminar de esperarla.

**Por qué:** el `release` original cancela la tarea y cierra inmediatamente los intérpretes. Al detener una pantalla o cambiar de contexto, una inferencia o lectura del micrófono podía continuar durante ese cierre. La integración espera su finalización antes de abrir otra sesión o liberar los modelos.

**Efecto en el algoritmo:** ninguno en la clasificación o en sus parámetros; solo cambia cómo se liberan recursos al finalizar una sesión. El diff completo está en `auditoria/sound-engine_src_main_java_com_indoamerica_haptica_soundengine_SoundEngine.kt.diff`.

**Assets y resto de fuentes del motor:** se conservan sin cambios, comprobados por SHA-256 en `auditoria/comparacion_fuentes.json`.

## 2. Cambios en el equipo musical

**Código de producción:** todos los archivos de `audioToHaptics/src/main/` se conservan idénticos, incluidos Basic Pitch y los algoritmos DSP, separación, transcripción y síntesis.

**Archivo de pruebas:** `audioToHaptics/src/test/kotlin/com/tesis/audiotohaptics/ModelIntegrationTest.kt`.

**Sección:** comienzo de `basicPitchKotlinMatchesNumpyReferenceAndPrototype`.

**Qué cambió:** se añadió una comprobación `Assume.assumeTrue` de que exista al menos una pareja de stem WAV y referencia CSV requerida por la comparación.

**Por qué:** los ZIP no incluyen esos recursos externos. La prueba original omitía todas las entradas, generaba un informe vacío y fallaba al comprobar que no estuviera vacío. Ahora se registra como omitida por falta de recursos; no se la da por superada. Si existen referencias vuelve a ejecutar las mismas comparaciones numéricas originales.

**Efecto en el algoritmo:** ninguno; solo afecta al diagnóstico de una prueba con datos ausentes. Su diff figura en `auditoria/`.

## 3. Adaptación de compilación

| Archivo o sección | Qué cambió | Por qué |
|---|---|---|
| `settings.gradle.kts` | Nombre V3Integracion1.0; módulos sound-engine y audioToHaptics; paquete de modelos opcional si ya existe Demucs | Compilar una aplicación unificada aunque el modelo grande no se haya entregado |
| `build.gradle.kts` raíz | Plugins Android library y serialización Kotlin alineados con la app | Incorporar las bibliotecas al proyecto AGP 9 |
| `app/build.gradle.kts` | Dependencias a los módulos, minSdk 26, Java 17, versión 3.0-integracion1.0 y ONNX sin comprimir | Cumplir los requisitos existentes de la biblioteca musical y empaquetar sus assets |
| `sound-engine/build.gradle.kts` | Android library explícito; Java 17; coroutines 1.9.0; LiteRT 1.4.2 con la misma API Interpreter | Usar las dependencias ya instaladas en V3 y evitar dos runtimes TensorFlow Lite duplicados |
| `audioToHaptics/build.gradle.kts` | Retirados plugin Kotlin Android y kotlinOptions antiguos; conserva Java 17, serialización y dependencias originales | Compatibilidad con Kotlin incorporado en AGP 9 |
| `audioToHapticsModels/` | Copiado sin cambios; inclusión condicional | Conservar la comprobación original de existencia y SHA-256 si el equipo entrega el modelo |
| `app/src/main/assets/yamnet.tflite` | Retirado de esta copia de la app | Evitar que el YAMNet anterior sobrescriba el del módulo recibido; sus formatos de salida son distintos |

No se renombraron los assets del equipo cotidiano para conservar su carga original. El módulo aporta yamnet.tflite, head.tflite, clases.txt y contexts.json al APK.

## 4. Adaptación de la aplicación y la interfaz

| Archivo o sección | Qué cambió | Por qué |
|---|---|---|
| `integracion/RegistroAlgoritmos.kt` | Construye SoundEngine y AudioToHaptics reales | Sustituir los motores pendientes en el flujo activo |
| `integracion/IntegracionViewModel.kt`, sesión cotidiana | Sesiones exclusivas de SoundEngine; suscripción a eventos antes de start; cierre esperado; contexto por sesión | Utilizar el micrófono y la inferencia del módulo recibido sin abrir un segundo AudioRecord ni aplicar el antiguo umbral global de 0,35 |
| Mismo archivo, salida cotidiana | Mapea ladrido/llanto/sirena/claxon a los comandos existentes, comprobando el perfil del firmware | Unificar con BLE sin inventar nuevos comandos; maquinaria/vidrio quedan como aviso visual |
| Mismo archivo, análisis musical | Llama process(uri); muestra estado real de modelos, progreso, cancelación, errores y resultados | La biblioteca procesa canciones completas y no encaja en el contrato anterior de fragmentos mono |
| Mismo archivo, configuración y vista previa | TestValues solo por selección explícita; escucha de los WAV resultantes | Mantener los parámetros de prueba identificados y la función de la demo original, sin afirmar reproducción multicanal física |
| `integracion/CatalogoCotidianoEquipo.kt` | Lee contexts.json y clases.txt para construir los casos de la interfaz | Mostrar las capacidades reales y distinguir clases configuradas de clases entrenadas |
| `ui/screens/PanelesEquipos.kt` | Contextos Hogar/Urbano/Social/Industrial; casos con umbral/prioridad; firmware separado; estado musical y canales | Ajustar la interfaz a ambos algoritmos sin modificar sus reglas |
| `ui/screens/ExperienceScreen.kt` | Inserta los paneles y mantiene el reproductor; pausa fuentes que compiten por el audio; elimina la antigua sincronización de notas pendiente | La biblioteca entrega tres archivos, y el firmware recibido no soporta reproducir esos tres canales musicales |

El selector de firmware Social/Alerta bicicleta sigue existiendo exclusivamente para elegir el conjunto de comandos admitidos. No reemplaza los cuatro contextos acústicos. El indicador de Inicio reutiliza la conexión GATT real de esta misma sesión.

Las clases antiguas de algoritmos pendientes, el YAMNet anterior y el secuenciador quedan como referencias de V3, sin uso en el flujo integrado. Sus pruebas históricas no prueban los nuevos motores; se añadieron pruebas específicas de integración.

## 5. Límites conservados

- Sin Demucs no se ejecuta el análisis musical completo. La app muestra el motivo y permite reproducción normal.
- Sin parámetros oficiales de actuadores no hay configuración confirmada; los valores de prueba son los mismos del equipo.
- Timbre, alarma de humo y nombre de usuario se muestran como pendientes de entrenamiento.
- Maquinaria y vidrio roto tienen detección visual, sin comandos nuevos para el ESP32.
- No se ha implementado transmisión de tres WAV musicales por BLE ni inventado una equivalencia con un vibrador de teléfono.
- La estimación de dB permanece sin calibración; no se presenta como medición fiable de distancia.
- Las comprobaciones de emulador y datos sintéticos no acreditan precisión acústica ni respuesta del hardware real.
