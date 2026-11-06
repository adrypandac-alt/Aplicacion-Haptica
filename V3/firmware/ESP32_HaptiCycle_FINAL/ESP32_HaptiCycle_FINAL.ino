#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <math.h>
#include <string.h>

// =========================================================
// HAPTICYCLE - FIRMWARE FINAL
// ESP32 clasico (ESP32-WROOM-32 / DevKit V1)
// BLE GATT + PAM8403 + 2 excitadores 8 ohm / 2 W
// =========================================================

// Deben coincidir EXACTAMENTE con la app Android.
static const char* NOMBRE_DISPOSITIVO = "VIBRAFIL_ESP32";
static const char* UUID_SERVICIO = "12345678-1234-1234-1234-1234567890ab";
static const char* UUID_CARACTERISTICA = "abcdefab-1234-5678-1234-abcdefabcdef";

// ESP32 clasico: DAC1 = GPIO25, DAC2 = GPIO26.
// GPIO25 -> entrada L del PAM8403
// GPIO26 -> entrada R del PAM8403
static const uint8_t PIN_IZQUIERDO = 25;
static const uint8_t PIN_DERECHO = 26;
static const uint8_t PIN_LED = 2;

// DAC ESP32: 0..255. El punto medio evita saltos de DC.
static const uint8_t NIVEL_REPOSO = 128;
static const int AMPLITUD_PRUEBA = 28;
static const int AMPLITUD_TACTON = 32;
static const int FRECUENCIA_MUESTREO = 8000;

BLEServer* servidorBle = nullptr;
BLECharacteristic* caracteristica = nullptr;
volatile bool telefonoConectado = false;
volatile bool detenerSolicitado = false;
bool estuvoConectado = false;

struct ComandoHaptico {
  char texto[16];
};

QueueHandle_t colaComandos = nullptr;

// =========================================================
// SALIDA HAPTICA
// =========================================================

void silenciar() {
  dacWrite(PIN_IZQUIERDO, NIVEL_REPOSO);
  dacWrite(PIN_DERECHO, NIVEL_REPOSO);
}

bool pausaInterrumpible(uint32_t ms) {
  const uint32_t inicio = millis();
  while (millis() - inicio < ms) {
    if (detenerSolicitado) return false;
    delay(2);
  }
  return true;
}

// canal: 0=izquierdo, 1=derecho, 2=ambos
bool seno(int canal, int frecuenciaHz, int duracionMs, int amplitud) {
  amplitud = constrain(amplitud, 5, 55);
  const int totalMuestras = (FRECUENCIA_MUESTREO * duracionMs) / 1000;
  const float incremento = 2.0f * PI * frecuenciaHz / FRECUENCIA_MUESTREO;
  float fase = 0.0f;

  for (int i = 0; i < totalMuestras; i++) {
    if (detenerSolicitado) {
      silenciar();
      return false;
    }

    const int valor = constrain(
      NIVEL_REPOSO + (int)(sinf(fase) * amplitud),
      0,
      255
    );

    dacWrite(PIN_IZQUIERDO, (canal == 0 || canal == 2) ? valor : NIVEL_REPOSO);
    dacWrite(PIN_DERECHO,   (canal == 1 || canal == 2) ? valor : NIVEL_REPOSO);

    fase += incremento;
    if (fase >= 2.0f * PI) fase -= 2.0f * PI;
    delayMicroseconds(125);
  }

  silenciar();
  return true;
}

void tactonSirena() {
  Serial.println("TACTON: SIREN");
  for (int i = 0; i < 3 && !detenerSolicitado; i++) {
    if (!seno(0, 175, 190, AMPLITUD_TACTON)) return;
    if (!pausaInterrumpible(45)) return;
    if (!seno(1, 175, 190, AMPLITUD_TACTON)) return;
    if (!pausaInterrumpible(55)) return;
  }
}

void tactonBocina() {
  Serial.println("TACTON: HORN");
  if (!seno(2, 190, 150, AMPLITUD_TACTON)) return;
  if (!pausaInterrumpible(100)) return;
  if (!seno(2, 190, 150, AMPLITUD_TACTON)) return;
  if (!pausaInterrumpible(120)) return;
  seno(2, 190, 430, AMPLITUD_TACTON);
}

void tactonAlarma() {
  Serial.println("TACTON: ALARM");
  for (int i = 0; i < 4 && !detenerSolicitado; i++) {
    if (!seno(0, 220, 110, AMPLITUD_TACTON)) return;
    if (!pausaInterrumpible(45)) return;
    if (!seno(1, 220, 110, AMPLITUD_TACTON)) return;
    if (!pausaInterrumpible(70)) return;
  }
}

void establecerRespuesta(const char* texto) {
  if (caracteristica != nullptr) {
    caracteristica->setValue(texto);
  }
}

void ejecutar(const char* cmd) {
  detenerSolicitado = false;
  silenciar();

  if (strcmp(cmd, "LEFT") == 0) {
    Serial.println("PRUEBA: LEFT");
    seno(0, 170, 900, AMPLITUD_PRUEBA);
    establecerRespuesta("DONE_LEFT");
  } else if (strcmp(cmd, "RIGHT") == 0) {
    Serial.println("PRUEBA: RIGHT");
    seno(1, 170, 900, AMPLITUD_PRUEBA);
    establecerRespuesta("DONE_RIGHT");
  } else if (strcmp(cmd, "BOTH") == 0) {
    Serial.println("PRUEBA: BOTH");
    seno(2, 170, 900, AMPLITUD_PRUEBA);
    establecerRespuesta("DONE_BOTH");
  } else if (strcmp(cmd, "SIREN") == 0) {
    tactonSirena();
    establecerRespuesta("DONE_SIREN");
  } else if (strcmp(cmd, "HORN") == 0) {
    tactonBocina();
    establecerRespuesta("DONE_HORN");
  } else if (strcmp(cmd, "ALARM") == 0) {
    tactonAlarma();
    establecerRespuesta("DONE_ALARM");
  }

  silenciar();
}

void recibirComando(String comando) {
  comando.trim();
  comando.toUpperCase();
  if (comando.length() == 0) return;

  Serial.print("Comando recibido: ");
  Serial.println(comando);

  if (comando == "PING") {
    establecerRespuesta("VIBRAFIL_READY");
    Serial.println("Respuesta: VIBRAFIL_READY");
    return;
  }

  if (comando == "STOP") {
    detenerSolicitado = true;
    if (colaComandos != nullptr) xQueueReset(colaComandos);
    silenciar();
    establecerRespuesta("STOPPED");
    Serial.println("Actuadores detenidos");
    return;
  }

  const bool valido =
    comando == "LEFT"  || comando == "RIGHT" || comando == "BOTH" ||
    comando == "SIREN" || comando == "HORN"  || comando == "ALARM";

  if (!valido) {
    establecerRespuesta("UNKNOWN_COMMAND");
    Serial.println("Comando no reconocido");
    return;
  }

  if (colaComandos == nullptr) return;

  // Una alerta nueva interrumpe el patrón anterior para no acumular tactones.
  detenerSolicitado = true;

  ComandoHaptico mensaje{};
  comando.toCharArray(mensaje.texto, sizeof(mensaje.texto));
  xQueueOverwrite(colaComandos, &mensaje);
}

void tareaHaptica(void*) {
  ComandoHaptico mensaje{};
  for (;;) {
    if (xQueueReceive(colaComandos, &mensaje, portMAX_DELAY) == pdTRUE) {
      ejecutar(mensaje.texto);
    }
  }
}

// =========================================================
// BLE
// =========================================================

class CallbacksServidor : public BLEServerCallbacks {
  void onConnect(BLEServer*) override {
    telefonoConectado = true;
    digitalWrite(PIN_LED, HIGH);
    Serial.println("Telefono conectado por BLE");
  }

  void onDisconnect(BLEServer*) override {
    telefonoConectado = false;
    detenerSolicitado = true;
    if (colaComandos != nullptr) xQueueReset(colaComandos);
    silenciar();
    digitalWrite(PIN_LED, LOW);
    Serial.println("Telefono desconectado");
  }
};

class CallbacksComandos : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    auto valor = c->getValue();
    if (valor.length() == 0) return;

    String comando;
    for (size_t i = 0; i < valor.length(); i++) {
      comando += (char)valor[i];
    }
    recibirComando(comando);
  }
};

void iniciarBle() {
  BLEDevice::init(NOMBRE_DISPOSITIVO);

  servidorBle = BLEDevice::createServer();
  servidorBle->setCallbacks(new CallbacksServidor());

  BLEService* servicio = servidorBle->createService(UUID_SERVICIO);
  caracteristica = servicio->createCharacteristic(
    UUID_CARACTERISTICA,
    BLECharacteristic::PROPERTY_READ |
    BLECharacteristic::PROPERTY_WRITE |
    BLECharacteristic::PROPERTY_WRITE_NR
  );

  caracteristica->setCallbacks(new CallbacksComandos());
  caracteristica->setValue("VIBRAFIL_READY");
  servicio->start();

  BLEAdvertising* publicidad = BLEDevice::getAdvertising();
  publicidad->addServiceUUID(UUID_SERVICIO);
  publicidad->setScanResponse(true);
  BLEDevice::startAdvertising();

  Serial.println("BLE listo");
  Serial.print("Nombre: "); Serial.println(NOMBRE_DISPOSITIVO);
  Serial.print("Servicio: "); Serial.println(UUID_SERVICIO);
  Serial.print("Caracteristica: "); Serial.println(UUID_CARACTERISTICA);
}

// =========================================================
// SETUP / LOOP
// =========================================================

void setup() {
  Serial.begin(115200);
  delay(600);

  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, LOW);
  silenciar();

  colaComandos = xQueueCreate(1, sizeof(ComandoHaptico));
  if (colaComandos == nullptr) {
    Serial.println("ERROR: no se pudo crear la cola haptica");
    while (true) delay(1000);
  }

  xTaskCreatePinnedToCore(
    tareaHaptica,
    "tarea_haptica",
    4096,
    nullptr,
    1,
    nullptr,
    1
  );

  iniciarBle();

  Serial.println();
  Serial.println("=== HAPTICYCLE LISTO ===");
  Serial.println("Monitor Serie 115200:");
  Serial.println("LEFT / RIGHT / BOTH");
  Serial.println("SIREN / HORN / ALARM");
  Serial.println("PING / STOP");
}

void loop() {
  if (Serial.available()) {
    recibirComando(Serial.readStringUntil('\n'));
  }

  if (!telefonoConectado && estuvoConectado) {
    delay(200);
    if (servidorBle != nullptr) {
      servidorBle->startAdvertising();
      Serial.println("BLE anunciandose nuevamente");
    }
    estuvoConectado = false;
  }

  if (telefonoConectado && !estuvoConectado) {
    estuvoConectado = true;
  }

  delay(20);
}
