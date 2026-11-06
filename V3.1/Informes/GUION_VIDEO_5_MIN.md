# Guion de video para tres personas

**Duración objetivo:** 5 minutos. Las pausas y las acciones en pantalla están incluidas en los tiempos. Graben con la APK actualizada y dejen unos segundos para que se lean las tarjetas.

## 0:00–1:00 · Persona 1 · Presentación

*En pantalla: inicio de la aplicación y sus accesos a Cotidiano y Música.*

Hola. Nuestro proyecto es una aplicación Android que convierte información de audio en señales visuales y, cuando hay un patrón disponible, en vibraciones. El objetivo es que una persona pueda identificar ciertos sonidos del entorno y explorar otra forma de percibir la música.

La aplicación tiene dos modos. En Cotidiano, el teléfono usa el micrófono y busca sonidos definidos para un contexto. Por ejemplo, en Hogar puede reconocer un ladrido. En Música, el usuario elige un archivo y la biblioteca puede generar señales separadas para voz, guitarra y percusión cuando están disponibles los modelos requeridos.

*Pausa para mostrar cada acceso. Dejar abierto Cotidiano.*

La diferencia principal está en la entrada y en el resultado. En un modo escuchamos el entorno y mostramos un aviso. En el otro, trabajamos con un archivo para preparar señales sincronizadas con la canción.

## 1:00–2:45 · Persona 2 · Demostración de Cotidiano

*En pantalla: seleccionar Hogar, bajar a Ladrido y pulsar Probar aviso y vibración.*

Aquí estamos en Hogar. Para la grabación vamos a usar una demostración manual. Al probar el aviso de ladrido aparece una tarjeta de color ámbar con una huella y el texto «Ladrido de perro». Una detección del micrófono aparece arriba, debajo del control de escucha, e incluye la confianza y el contexto.

*Dejar visible la tarjeta. Después, seleccionar Urbano y probar Sirena.*

Ahora cambiamos a Urbano y probamos el aviso de sirena. El color cambia a rojo y aparece un altavoz como símbolo. El modelo reconoce la categoría «sirena» y por eso la pantalla dice «Sirena de emergencia». El sonido podría venir de una ambulancia u otro vehículo de emergencia.

*Mostrar por unos segundos el botón de micrófono, sin iniciar escucha si la grabación es una demostración.*

Cuando se activa el micrófono, el programa toma pequeños fragmentos de audio. YAMNet convierte cada fragmento en números que representan sus características. Un segundo modelo calcula la confianza para las clases del proyecto. El motor comprueba si ese sonido está permitido en el contexto, si supera los umbrales y si se repite en dos ventanas consecutivas. Así llega el evento que activa la tarjeta y, si hay un patrón compatible, la vibración.

## 2:45–4:30 · Persona 3 · Código e integración

*En pantalla: mostrar `RegistroAlgoritmos.kt` y señalar las dos funciones de creación. Después, mostrar `IntegracionViewModel.kt`.*

La unión se hizo en Android. Cada algoritmo vive en un módulo de Gradle. `sound-engine` trabaja con el micrófono y `audioToHaptics` procesa el archivo musical. La aplicación incluye ambos módulos. Este archivo, `RegistroAlgoritmos`, crea la instancia del motor que necesita cada modo.

*Señalar `next.events.collect` y luego `AudioToHaptics.process(uri)`.*

El punto de encuentro es `IntegracionViewModel`. En Cotidiano, se suscribe a los eventos del motor y guarda el último. Compose lee la clase para mostrar su tarjeta y el ViewModel decide si también corresponde emitir una señal háptica. En Música, el ViewModel entrega a la biblioteca la ubicación del archivo y recibe el progreso y el resultado. La aplicación detiene la escucha del micrófono al iniciar un análisis musical.

*Mostrar el panel Música y los indicadores de modelos.*

La biblioteca musical usa Demucs para separar fuentes y Basic Pitch para transcribir notas. Luego genera señales para los tres canales. Esta versión tiene Basic Pitch y reproduce archivos locales. El análisis completo requiere el archivo de Demucs, que está pendiente en la entrega. En la explicación escrita dejamos el peso medido de la APK y la estimación con ese modelo.

## 4:30–5:00 · Cierre entre las tres personas

*En pantalla: volver a la tarjeta de ladrido o sirena.*

**Persona 1.** Tenemos una sola aplicación con dos modos que resuelven tareas diferentes a partir de audio.

**Persona 2.** En Cotidiano, el color, el símbolo y el nombre permiten distinguir rápidamente el sonido reconocido.

**Persona 3.** La interfaz y el ViewModel coordinan los dos módulos. Cada módulo conserva su propia entrada, su procesamiento y sus resultados.
