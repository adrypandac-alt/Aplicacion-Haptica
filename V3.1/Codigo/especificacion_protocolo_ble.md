# Especificación del Protocolo BLE - MusicHaptic
## Documento para el equipo de Desarrollo Móvil (5to Semestre)

### Contexto
La app actual utiliza **Bluetooth Clásico (SPP/RFCOMM)** para comunicarse entre dispositivos.
Sin embargo, el hardware del brazalete usa un **ESP32-H2** que **solo soporta BLE** (Bluetooth Low Energy).
Este documento define el protocolo BLE necesario para conectar la app con el brazalete.

---

## 1. Servicio GATT

| Campo | Valor |
|-------|-------|
| **Service UUID** | `12345678-1234-1234-1234-123456789abc` |
| **Characteristic UUID** (escritura) | `12345678-1234-1234-1234-000000000001` |
| **Characteristic UUID** (notificación) | `12345678-1234-1234-1234-000000000002` |
| **Nombre del dispositivo** | `MusicHaptic-Bracelet` |

## 2. Formato del Comando (App -> ESP32)

La app escribe en la **característica de escritura** un JSON compacto:

```json
{"n":"DO","f":62.15,"u":0.0,"a":255,"d":500}
```

| Campo | Tipo | Descripción | Rango |
|-------|------|-------------|-------|
| `n` | String | Nombre de la nota | DO, DO#, RE...SI |
| `f` | Float | Frecuencia en Hz | 62.15 - 553.83 |
| `u` | Float | Ubicación espacial | 0.0 (muñeca) - 10.0 (codo) |
| `a` | Int | Amplitud de vibración | 0 - 255 |
| `d` | Int | Duración en milisegundos | 50 - 5000 |

## 3. Formato de Respuesta (ESP32 -> App)

El ESP32 notifica por la **característica de notificación**:

```json
{"s":"OK","n":"DO"}
```

| Campo | Valores | Significado |
|-------|---------|-------------|
| `s` | `OK` | Nota reproducida exitosamente |
| `s` | `ERR` | Error al activar actuador |
| `s` | `CAL` | Solicita calibración |
| `n` | String | Nombre de la nota procesada |

## 4. Flujo de Conexión

```
APP                                    ESP32
                                        
   1. Scan BLE "MusicHaptic-Bracelet"   
  >
                                        
   2. Connect + Discover Services       
  >
                                        
   3. Subscribe to Notifications        
  >
                                        
   4. Write: {"n":"DO","f":62.15,...}   
  >
                                        
   5. Notify: {"s":"OK","n":"DO"}       
  <
                                        
   6. (Repetir 4-5 por cada nota)       
```

## 5. Cambios Necesarios en la App

### Archivo: `HapticService.kt` (línea 44)
**Actual:** Envía solo el nombre de la nota como texto por socket SPP
```kotlin
writer.println(nota.nombre)  // Solo envía "DO"
```

**Propuesto:** Enviar JSON completo por BLE GATT Write
```kotlin
val comando = """{"n":"${nota.nombre}","f":${nota.frecuenciaHz},"u":${nota.ubicacion},"a":${amplitud},"d":${duracion}}"""
bleCharacteristic.setValue(comando.toByteArray())
bleGatt.writeCharacteristic(bleCharacteristic)
```

### Archivo: `HapticReceiverService.kt`
**Actual:** Usa `BluetoothServerSocket` con RFCOMM (Bluetooth Clásico)

**Propuesto:** Reemplazar con `BluetoothGattServer` (BLE):
- Registrar el servicio GATT con los UUIDs de arriba
- En `onCharacteristicWriteRequest`, parsear el JSON y ejecutar `emitirTacton()`
- Enviar respuesta por notificación

### Archivo: `AppGlobals.kt`
**Actual:** `var socketBluetooth: BluetoothSocket?` (socket SPP)

**Propuesto:** Agregar:
```kotlin
var bleGatt: BluetoothGatt? = null
var bleCharacteristic: BluetoothGattCharacteristic? = null
var estaBrazaleteConectado: Boolean = false
```

## 6. Tabla de Notas (Referencia compartida)

Estos valores ya están en `BaseDatosHelper.kt` (método `insertarNotasBaseGlobales`):

| Nota | Hz | Ubicación | Amplitud |
|------|-----|-----------|----------|
| DO | 62.15 | 0.0 | 255 |
| DO# | 75.82 | 0.9 | 245 |
| RE | 92.50 | 1.8 | 240 |
| RE# | 112.85 | 2.7 | 235 |
| MI | 137.68 | 3.6 | 230 |
| FA | 167.97 | 4.5 | 225 |
| FA# | 204.92 | 5.4 | 220 |
| SOL | 250.00 | 6.3 | 225 |
| SOL# | 305.00 | 7.2 | 230 |
| LA | 372.10 | 8.1 | 235 |
| LA# | 453.96 | 9.0 | 245 |
| SI | 553.83 | 10.0 | 255 |
