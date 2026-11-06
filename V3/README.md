# V3Integracion1.0

Aplicación Android que unifica la interfaz PrototipoV3 con los componentes Android de los proyectos `sound-recognition` (Cotidiano) y `Tesis/AudioToHaptics` (Audio). No genera ni descarga modelos.

## Abrir y compilar

Abrir esta carpeta en Android Studio. Se requieren JDK 21 para Gradle, Android SDK 37 y 35, y Android 8/API 26 o superior para instalar.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :audioToHaptics:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest  # emulador o teléfono conectado
```

Las dependencias de compilación se obtienen por Gradle. El reconocimiento y el procesamiento musical no descargan modelos al ejecutarse. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Cotidiano

La interfaz carga contextos, prioridades y umbrales de `sound-engine/src/main/assets/contexts.json`. Contrasta cada clase con `clases.txt` para mostrar qué reconoce realmente el modelo:

| Contexto | Detecciones disponibles | Definidos pero sin entrenar |
|---|---|---|
| Hogar | Llanto de bebé, vidrio roto, ladrido | Alarma de humo, timbre, nombre del usuario |
| Urbano | Sirena, claxon | Nombre del usuario |
| Social | Sirena | Nombre del usuario, alarma de humo |
| Industrial | Maquinaria, sirena | Alarma de humo, nombre del usuario |

Esta distribución es la del equipo: no se ha cambiado para hacerla coincidir con los perfiles anteriores de V3.

Para escuchar: abrir Cotidiano, elegir contexto, pulsar Iniciar escucha y conceder permiso al micrófono. Cambiar de contexto detiene la sesión; pulsar Iniciar escucha para usar el nuevo modo. Los botones Probar patrón son demostraciones manuales.

El perfil del firmware es independiente del contexto de escucha. Elegir Social o Alerta bicicleta según el firmware instalado, conceder permisos y conectar la háptica. Se reutilizan los comandos existentes para ladrido, bebé, sirena y claxon. Maquinaria y vidrio roto se muestran en pantalla; no se inventan patrones ni comandos para esos sonidos. Si el perfil no acepta una alerta con patrón existente, se usa el teléfono. El estado Conectada comprueba el servicio GATT, no la ejecución física del motor.

## Música

El reproductor sigue abriendo archivos locales. La biblioteca `audioToHaptics` está incorporada y el análisis llama a su API original `AudioToHaptics.process(uri)` con progreso, cancelación y presentación de los tres canales, MIDI, eventos y manifiesto. La vista previa escucha los WAV, igual que la demo del equipo; no transmite tres canales al ESP32.

**En esta entrega falta `htdemucs_6s.onnx`.** Basic Pitch sí está incluido. Por eso el análisis está deshabilitado con explicación; la reproducción normal funciona. No se ha generado el modelo faltante ni modificado el firmware.

Si el equipo entrega el archivo validado, colocarlo en `models/htdemucs_6s.onnx` y recompilar. `audioToHapticsModels` se incluye automáticamente solo si existe; su tarea original comprueba SHA-256 `16ebcff7c392fc1a21bd2574ea686d20c0ffd9abfd7dcb56680359cb5450c0e0`. No basta con instalar esta APK y copiar el modelo al teléfono.

La configuración oficial de actuadores sigue pendiente. La opción Usar configuración de prueba activa explícitamente los valores originales de laboratorio (150/200/250 Hz y 44,1 kHz); no se presentan como parámetros confirmados.

## Documentación

- `docs/CAMBIOS_INTEGRACION.md`: qué se cambió, por qué y en qué secciones.
- `docs/VALIDACION.md`: pruebas y límites de la entrega.
- `docs/auditoria/`: huellas SHA-256 y diferencias respecto a los proyectos recibidos.
- `docs/ANALISIS_PREVIO.md`: análisis histórico anterior a esta integración.

Se conservaron las fuentes originales fuera de esta carpeta. Para repetir la comparación, ejecutar `python scripts/verificar_fuentes.py` con las carpetas Audio y Cotidiano como hermanas de esta carpeta.
