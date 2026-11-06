# Revisión UX/UI — 29 de octubre de 2026

Alcance: pantalla activa ExperienceScreen de V3Integracion1.0, Inicio, Cotidiano y conexión BLE.

## Problemas encontrados y cambios

- La lista completa de sonidos desplazaba la acción principal de Cotidiano. Ahora aparecen primero los contextos, después el interruptor de micrófono (verde activo, gris apagado) y su resultado, y luego un resumen compacto de la háptica. Los detalles de sonidos, pruebas y ajustes quedan después.
- Inicio enviaba a Cotidiano para resolver la conexión. Ahora la tarjeta Estado de conexión abre una pantalla dedicada de dispositivos, accesible también desde Cotidiano.
- La búsqueda conectaba automáticamente al primer dispositivo compatible. Ahora muestra dispositivos BLE cercanos por nombre y dirección, prioriza los que anuncian compatibilidad y permite seleccionar Conectar. Los que no anuncian compatibilidad se identifican y no se ofrecen como hápticas.
- La pantalla distingue el dispositivo conectado de los cercanos, ofrece desconexión y cancelación, y termina con el resultado de conexión.
- Se usan verde para conexión verificada, ámbar para operación pendiente y rojo para desconexión. Cada color se acompaña de texto y símbolo para no depender de la percepción del color.
- Se conserva la verificación del servicio GATT y la característica de escritura; aparecer en una búsqueda no equivale a estar conectado. Se añade un límite de espera al conectar el dispositivo elegido.
- Volver de Android regresa a Inicio; se conserva la sección seleccionada ante recreación de la actividad.

## Validación

Compilación assembleDebug y pruebas testDebugUnitTest completadas correctamente. No hay teléfono ni emulador conectado: quedan pendientes la revisión visual en dispositivo, los permisos reales y la prueba física con la háptica. El estado conectado confirma el servicio de comandos, no el movimiento físico del actuador.

## Recomendaciones siguientes

Revisar en teléfono con tamaño de fuente ampliado y con lectores de pantalla; comprobar búsqueda vacía, Bluetooth apagado, permiso denegado, conexión incompatible y pérdida de conexión con el firmware real. Los módulos de Música y las pantallas antiguas de acceso quedan fuera de este cambio.
