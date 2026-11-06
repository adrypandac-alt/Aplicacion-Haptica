# Corrección de instalación 1.0.1

Se reportó un aviso de conflicto de paquetes al instalar la APK en un teléfono. La entrega anterior compartía applicationId con las versiones previas. Sin un registro de instalación del teléfono no se puede distinguir con certeza entre una firma diferente, una versión incompatible u otro conflicto del instalador.

Para permitir instalación independiente, se cambió solamente la identidad de la app:

- applicationId: `com.rommelguerra.prototipomusichaptic.integracionv3`.
- Nombre visible: `Haptic V3 Integración`.
- versionCode: `6`.
- versionName: `3.0-integracion1.0.1`.
- La prueba de contexto Android se actualizó para comprobar la identidad nueva.

El namespace de Kotlin permanece igual. No se cambiaron modelos, algoritmos, contextos, firmware ni permisos de funcionamiento. Android administra esta edición como una aplicación independiente de las versiones con el identificador anterior; sus datos y permisos también son independientes.

APK para instalar: `output/apk/HapticV3-Integracion-1.0.1.apk`. El PDF de unificación describe la entrega base; este registro documenta el ajuste posterior de instalación.
