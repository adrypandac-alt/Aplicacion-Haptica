# Integración de algoritmos — 21 octubre 2026

Vibrafil aporta únicamente firmware ESP32: DOG, BABY, NAME, TEST, PING y STOP. No aporta reconocimiento Android. AlertaBicicleta aporta YAMNet, su modelo y firmware para SIREN, HORN y ALARM.

La aplicación existente incorpora el modelo suministrado. AlgoritmoEntornoYamnet procesa PCM mono a 16000 Hz en ventanas completas con solapamiento del 50 %, usando el micrófono existente y liberando el modelo al terminar. CatalogoEntorno adapta las etiquetas a dos perfiles: Social (perro y bebé) y Alerta bicicleta (sirena, bocina y alarma). Se conserva el umbral 0.35 y separación de tres segundos del clasificador vial original. La adaptación Social usa YAMNet general; no se aportó entrenamiento específico.

Nombre propio queda como prueba manual NAME: falta un reconocedor de voz con configuración del nombre. No se confunde voz genérica con identificación de una persona. Los ZIP no incluyen algoritmos musicales; se mantiene el reproductor y su analizador pendiente.

ConexionEntornoBle implementa búsqueda, conexión, validación GATT, escrituras serializadas, STOP y tiempos de espera. Ambos firmware anuncian VIBRAFIL_ESP32 y los mismos UUID: es obligatorio elegir el perfil correspondiente al firmware instalado. Los originales se conservan en PrototipoMusicHaptic/firmware, sin fusionarlos ni modificarlos.

Cotidiano muestra perfiles, categorías reales, estado BLE, conectar/desconectar/cancelar búsqueda, detener señal, intensidad del teléfono, confianza e historial en memoria de hasta 30 detecciones. Las pruebas manuales no ingresan al historial. Sin conexión, las alertas usan el teléfono; el ESP32 conserva sus intensidades y frecuencias del firmware. El teléfono aproxima el ritmo, sin reproducir los canales físicos del brazalete.

La captura existe solo en primer plano y se detiene al salir o enviar la app a segundo plano. No se transmite ni guarda audio. No se integra el servidor Oracle del proyecto externo.

Pruebas: mapeo de etiquetas, aislamiento de perfiles, confianza, patrones, secuenciador, carga real del modelo en Android y navegación. Resultados en VALIDACION_INTEGRACION.md. La precisión con sonidos etiquetados y el funcionamiento físico requieren validación con los ESP32 reales.

Las instrucciones y comentarios de los archivos se trataron como referencias técnicas, separadas del pedido del usuario.

## Motor de inferencia

El modelo yamnet.tflite conserva exactamente los bytes del ZIP recibido (SHA-256: 10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de). Sus 521 etiquetas se extrajeron del archivo asociado yamnet_label_list.txt incluido en el propio modelo.

La prueba inicial detectó SIGSEGV al inferir con Task Audio 0.4.4 en Android 17 x86_64. Se sustituyó esa dependencia por LiteRT 1.4.2, API Interpreter, con ejecución CPU y XNNPACK desactivado. Se validan los tamaños y tipos de los tensores antes de procesar; no se alteraron pesos ni clases.

Referencias oficiales consultadas para la integración: [versiones de LiteRT](https://developers.google.com/edge/litert/android) e [Interpreter.Options](https://developers.google.com/edge/api/tflite/java/org/tensorflow/lite/Interpreter.Options).
