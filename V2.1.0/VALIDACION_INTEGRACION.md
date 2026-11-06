# Validación de la integración — 21 octubre 2026

Versión Android 2.1.0, versionCode 4. Proyecto: PrototipoMusicHaptic.

## Resultado

- Compilación APK debug: correcta.
- 11 pruebas JVM: aprobadas, sin fallos. Incluyen aislamiento Social/Vial, etiquetas, scores inválidos, umbral, comandos, patrones, secuenciador y acumulación de eventos.
- 5 pruebas Android en emulador Android 17 / API 37, x86_64: aprobadas, sin fallos.
- Pruebas Android: decodificación PCM, identidad de aplicación, navegación y pruebas manuales, carga/inferencia/cierre del modelo real en ambos perfiles, captura real de micrófono, cancelación y reapertura de AudioRecord.
- Revisión visual de capturas Social y Bicicleta: controles accesibles mediante desplazamiento, tarjetas sin solapamientos y navegación inferior disponible.

## Corrección detectada durante la validación

La dependencia Task Audio 0.4.4 incluida en el proyecto recibido provocaba un SIGSEGV en el delegado nativo durante la inferencia en este emulador. Se reemplazó por LiteRT 1.4.2 Interpreter CPU con XNNPACK desactivado. El mismo modelo y sus etiquetas superaron después las pruebas reales de inferencia. No se modificaron sus pesos.

## Evidencias

Registros en la carpeta del espacio de trabajo: integracion-validacion-final.log e integracion-interfaz-final.log. Informes Gradle en app/build/reports/tests y app/build/reports/androidTests. Capturas copiadas a Capturas Android/Integracion/HapticMusicIntegracion.

## Pendiente de validación física

No había un ESP32 conectado. No se verificaron radio BLE, excitadores, canales ni respuesta física STOP. Los contratos de UUID y comandos coinciden con los archivos aportados; hay que seleccionar el perfil del firmware instalado, porque ambos anuncian el mismo dispositivo.

No se proporcionaron audios etiquetados para medir exactitud o falsos positivos. Las pruebas de silencio comprueban ejecución y ausencia de detecciones espurias en esa entrada; no demuestran exactitud en un entorno real. El umbral 0.35 del proyecto vial original debe calibrarse con el equipo.

Nombre propio conserva únicamente el patrón manual NAME: el ZIP Social no contiene reconocimiento de voz. Los ZIP tampoco contienen un algoritmo musical; el reproductor existente se conserva con ese analizador pendiente.
