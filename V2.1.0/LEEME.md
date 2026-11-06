# HapticMusic · Prototipo PISP 5 · versión 2.1.0

Aplicación Android Kotlin con Jetpack Compose. Abra PrototipoMusicHaptic en Android Studio, use JDK 21 y Android SDK 37. Compatible desde Android 7.

APK: APK/Prototipo-PISP-5.apk.

En Cotidiano seleccione Vibrafil Social o Alerta bicicleta. Puede iniciar reconocimiento por micrófono, conectar el ESP32 y probar señales manualmente. El modelo YAMNet proporcionado está incluido y funciona localmente. Nombre propio solo tiene prueba manual; los archivos no incluyen el reconocedor de nombres. Música conserva la reproducción y el analizador pendiente.

Ambos ESP32 tienen el mismo nombre BLE: seleccione el perfil del firmware que instaló. Los originales están en PrototipoMusicHaptic/firmware. El control de intensidad afecta al teléfono; el ESP32 utiliza su firmware.

Consulte INTEGRACION_ALGORITMOS.md y VALIDACION_INTEGRACION.md para detalles y límites.

Compilar: .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
