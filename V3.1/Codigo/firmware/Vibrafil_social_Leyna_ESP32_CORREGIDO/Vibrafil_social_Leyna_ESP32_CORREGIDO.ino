#include <Arduino.h>
#include <BLEAdvertising.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <atomic>
#include <ctype.h>
#include <string.h>
#include <math.h>

// =========================================================
// VIBRAFIL - FIRMWARE BLE social_Leyna
// =========================================================
// IMPORTANTE:
// Aplicacion oficial: Vibrafil. Identidad interna: social_Leyna (lider: Leyna).
// El nombre BLE tecnico VIBRAFIL_ESP32 se conserva para no romper
// instalaciones anteriores de la app ni el filtro existente.
// 1. El reconocimiento de perro, bebe y nombre ocurre en Android.
// 2. El ESP32 recibe DOG, BABY o NAME y ejecuta el patron haptico.
// 3. GPIO18 debe conectarse a la ENTRADA de un driver/controlador.
//    Nunca conecte un motor ERM/LRA directamente al GPIO del ESP32.
// =========================================================

// ------------------------- BLE ----------------------------
namespace BleConfig {
constexpr char DEVICE_NAME[] = "VIBRAFIL_ESP32";
constexpr char SERVICE_UUID[] =
    "12345678-1234-1234-1234-1234567890ab";
constexpr char COMMAND_UUID[] =
    "abcdefab-1234-5678-1234-abcdefabcdef";
constexpr char READY_MESSAGE[] = "VIBRAFIL_READY";

// Flags BLE estandar: general discoverable + sin BR/EDR.
// Se usa el valor directo para evitar depender de cabeceras GAP internas.
constexpr uint8_t ADVERTISING_FLAGS = 0x06;
}  // namespace BleConfig

// ----------------------- HARDWARE -------------------------
namespace HardwareConfig {
// Cambie estos pines solo si su circuito usa otros.
constexpr int LED_PIN = 2;
constexpr int HAPTIC_DRIVER_PIN = 18;

// Permite adaptar modulos cuyo pin EN/TRIG sea activo en nivel bajo.
constexpr bool LED_ACTIVE_HIGH = true;
constexpr bool HAPTIC_ACTIVE_HIGH = true;

// Los dos excitadores de bobina de voz conectados al PAM8403 usan
// los DAC GPIO25/GPIO26 del ESP32 clasico. En otras variantes (C3/S3/C6)
// esta salida se desactiva automaticamente para permitir la compilacion.
#ifndef SOCIAL_LEYNA_ENABLE_AUDIO
    #if defined(CONFIG_IDF_TARGET_ESP32)
        #define SOCIAL_LEYNA_ENABLE_AUDIO 1
    #else
        #define SOCIAL_LEYNA_ENABLE_AUDIO 0
    #endif
#endif

#if SOCIAL_LEYNA_ENABLE_AUDIO
constexpr int AUDIO_LEFT_PIN = 25;
constexpr int AUDIO_RIGHT_PIN = 26;
#endif
}  // namespace HardwareConfig

// ----------------------- PATRONES -------------------------
struct HapticPattern {
    uint8_t repetitions;
    uint16_t activeMs;
    uint16_t pauseMs;
    uint16_t tactileFrequencyHz;
    uint8_t tactileAmplitude;
};

namespace Patterns {
// Frecuencias bajas pensadas para sensacion tactil en excitadores de 8 ohmios.
constexpr HapticPattern TEST{2, 180, 120, 160, 60};
constexpr HapticPattern DOG{3, 500, 180, 180, 90};
constexpr HapticPattern BABY{3, 300, 150, 140, 72};
constexpr HapticPattern NAME{2, 180, 120, 220, 55};
}  // namespace Patterns

enum class CommandType : uint8_t {
    NONE,
    TEST,
    DOG,
    BABY,
    NAME
};

struct QueuedCommand {
    CommandType type;
    uint32_t epoch;
};

// ------------------------ ESTADO --------------------------
BLEServer* bleServer = nullptr;
BLECharacteristic* commandCharacteristic = nullptr;
BLEAdvertising* bleAdvertising = nullptr;
QueueHandle_t commandQueue = nullptr;

volatile bool deviceConnected = false;
volatile bool previousDeviceConnected = false;
volatile bool advertisingRestartRequested = false;

std::atomic<bool> stopRequested{false};
std::atomic<uint32_t> commandEpoch{0};

bool bleReady = false;

// =========================================================
// CONTROL SEGURO DE SALIDAS
// =========================================================

inline uint8_t activeLevel(bool activeHigh) {
    return activeHigh ? HIGH : LOW;
}

inline uint8_t inactiveLevel(bool activeHigh) {
    return activeHigh ? LOW : HIGH;
}

void setLed(bool enabled) {
    if (HardwareConfig::LED_PIN >= 0) {
        digitalWrite(
            HardwareConfig::LED_PIN,
            enabled
                ? activeLevel(HardwareConfig::LED_ACTIVE_HIGH)
                : inactiveLevel(HardwareConfig::LED_ACTIVE_HIGH)
        );
    }
}

void setHapticDriver(bool enabled) {
    digitalWrite(
        HardwareConfig::HAPTIC_DRIVER_PIN,
        enabled
            ? activeLevel(HardwareConfig::HAPTIC_ACTIVE_HIGH)
            : inactiveLevel(HardwareConfig::HAPTIC_ACTIVE_HIGH)
    );
}

#if SOCIAL_LEYNA_ENABLE_AUDIO
void setAudioIdle() {
    // Punto medio para una senal DAC bipolar acoplada al amplificador.
    dacWrite(HardwareConfig::AUDIO_LEFT_PIN, 128);
    dacWrite(HardwareConfig::AUDIO_RIGHT_PIN, 128);
}
#else
inline void setAudioIdle() {}
#endif

void outputsOff() {
    setLed(false);
    setHapticDriver(false);
    setAudioIdle();
}

void requestImmediateStop() {
    stopRequested.store(true, std::memory_order_release);
    outputsOff();
}

bool commandStillValid(uint32_t epoch) {
    return !stopRequested.load(std::memory_order_acquire) &&
           epoch == commandEpoch.load(std::memory_order_acquire);
}

bool interruptibleDelay(uint32_t durationMs, uint32_t epoch) {
    const uint32_t start = millis();

    while (static_cast<uint32_t>(millis() - start) < durationMs) {
        if (!commandStillValid(epoch)) {
            return false;
        }

        // Cede tiempo a BLE, sistema operativo y watchdog.
        delay(4);
    }

    return true;
}

// =========================================================
// EXCITACION TACTIL PAM8403 (ACTIVA EN ESP32 CLASICO)
// =========================================================

#if SOCIAL_LEYNA_ENABLE_AUDIO
void playToneStereo(
    uint16_t frequency,
    uint16_t durationMs,
    uint8_t amplitude,
    uint32_t epoch
) {
    if (frequency == 0 || durationMs == 0 || !commandStillValid(epoch)) {
        return;
    }

    constexpr uint32_t SAMPLE_RATE = 8000;
    constexpr uint32_t SAMPLE_PERIOD_US = 1000000UL / SAMPLE_RATE;
    constexpr float VIBRAFIL_TWO_PI = 6.28318530718f;

    frequency = static_cast<uint16_t>(
        constrain(static_cast<int>(frequency), 20, 3500)
    );
    amplitude = static_cast<uint8_t>(
        constrain(static_cast<int>(amplitude), 5, 120)
    );

    const uint32_t totalSamples =
        (SAMPLE_RATE * static_cast<uint32_t>(durationMs)) / 1000UL;

    const float phaseStep =
        VIBRAFIL_TWO_PI * static_cast<float>(frequency) /
        static_cast<float>(SAMPLE_RATE);

    float phase = 0.0f;

    for (uint32_t index = 0; index < totalSamples; ++index) {
        if (!commandStillValid(epoch)) {
            break;
        }

        const int value =
            128 + static_cast<int>(sinf(phase) * amplitude);
        const uint8_t dacValue =
            static_cast<uint8_t>(constrain(value, 0, 255));

        dacWrite(HardwareConfig::AUDIO_LEFT_PIN, dacValue);
        dacWrite(HardwareConfig::AUDIO_RIGHT_PIN, dacValue);

        phase += phaseStep;
        if (phase >= VIBRAFIL_TWO_PI) {
            phase -= VIBRAFIL_TWO_PI;
        }

        delayMicroseconds(SAMPLE_PERIOD_US);

        if ((index & 0x3FU) == 0U) {
            yield();
        }
    }

    setAudioIdle();
}


#else
inline void playToneStereo(
    uint16_t,
    uint16_t,
    uint8_t,
    uint32_t
) {}
#endif

// =========================================================
// EJECUCION HAPTICA
// =========================================================

const HapticPattern* patternFor(CommandType type) {
    switch (type) {
        case CommandType::TEST:
            return &Patterns::TEST;
        case CommandType::DOG:
            return &Patterns::DOG;
        case CommandType::BABY:
            return &Patterns::BABY;
        case CommandType::NAME:
            return &Patterns::NAME;
        default:
            return nullptr;
    }
}

const char* commandName(CommandType type) {
    switch (type) {
        case CommandType::TEST:
            return "TEST";
        case CommandType::DOG:
            return "DOG";
        case CommandType::BABY:
            return "BABY";
        case CommandType::NAME:
            return "NAME";
        default:
            return "NONE";
    }
}

bool runHapticPattern(
    const HapticPattern& pattern,
    uint32_t epoch
) {
    for (uint8_t index = 0; index < pattern.repetitions; ++index) {
        if (!commandStillValid(epoch)) {
            return false;
        }

        // El modulo LRA de GPIO18 y los dos excitadores del PAM8403
        // se activan al mismo tiempo para conservar un patron unico.
        setHapticDriver(true);
        setLed(true);

#if SOCIAL_LEYNA_ENABLE_AUDIO
        playToneStereo(
            pattern.tactileFrequencyHz,
            pattern.activeMs,
            pattern.tactileAmplitude,
            epoch
        );

        if (!commandStillValid(epoch)) {
            outputsOff();
            return false;
        }
#else
        if (!interruptibleDelay(pattern.activeMs, epoch)) {
            outputsOff();
            return false;
        }
#endif

        setHapticDriver(false);
        setLed(false);
        setAudioIdle();

        if (index + 1U < pattern.repetitions &&
            !interruptibleDelay(pattern.pauseMs, epoch)) {
            outputsOff();
            return false;
        }
    }

    outputsOff();
    return true;
}

void processCommand(const QueuedCommand& command) {
    if (command.type == CommandType::NONE ||
        command.epoch != commandEpoch.load(std::memory_order_acquire)) {
        return;
    }

    const HapticPattern* pattern = patternFor(command.type);
    if (pattern == nullptr) {
        return;
    }

    outputsOff();
    stopRequested.store(false, std::memory_order_release);

    if (!commandStillValid(command.epoch)) {
        outputsOff();
        return;
    }

    Serial.println();
    Serial.print("Comando ejecutado: ");
    Serial.println(commandName(command.type));

    runHapticPattern(*pattern, command.epoch);

    outputsOff();

    // No borra una solicitud STOP o una orden nueva que haya llegado
    // mientras se ejecutaba el patron anterior.
    if (command.epoch == commandEpoch.load(std::memory_order_acquire)) {
        stopRequested.store(false, std::memory_order_release);
    }
}

// =========================================================
// PARSEO Y COLA DE COMANDOS
// =========================================================

size_t normalizeCommand(
    const char* source,
    size_t sourceLength,
    char* destination,
    size_t destinationSize
) {
    if (source == nullptr || destination == nullptr || destinationSize == 0) {
        return 0;
    }

    size_t begin = 0;
    while (begin < sourceLength && isspace(static_cast<unsigned char>(source[begin]))) {
        ++begin;
    }

    size_t end = sourceLength;
    while (end > begin && isspace(static_cast<unsigned char>(source[end - 1]))) {
        --end;
    }

    size_t outputLength = 0;
    for (size_t index = begin;
         index < end && outputLength + 1 < destinationSize;
         ++index) {
        destination[outputLength++] = static_cast<char>(
            toupper(static_cast<unsigned char>(source[index]))
        );
    }

    destination[outputLength] = '\0';
    return outputLength;
}

CommandType parseHapticCommand(const char* command) {
    if (strcmp(command, "TEST") == 0) {
        return CommandType::TEST;
    }
    if (strcmp(command, "DOG") == 0) {
        return CommandType::DOG;
    }
    if (strcmp(command, "BABY") == 0) {
        return CommandType::BABY;
    }
    if (strcmp(command, "NAME") == 0) {
        return CommandType::NAME;
    }
    return CommandType::NONE;
}

void invalidatePendingCommands() {
    commandEpoch.fetch_add(1, std::memory_order_acq_rel);

    if (commandQueue != nullptr) {
        xQueueReset(commandQueue);
    }

    requestImmediateStop();
}

bool queueLatestCommand(CommandType type) {
    if (type == CommandType::NONE || commandQueue == nullptr) {
        return false;
    }

    // El evento mas reciente reemplaza al anterior.
    const uint32_t newEpoch =
        commandEpoch.fetch_add(1, std::memory_order_acq_rel) + 1U;

    requestImmediateStop();

    QueuedCommand command{type, newEpoch};

    return xQueueOverwrite(
        commandQueue,
        &command
    ) == pdPASS;
}

void sendReadyResponse() {
    if (!deviceConnected || commandCharacteristic == nullptr) {
        return;
    }

    commandCharacteristic->setValue(BleConfig::READY_MESSAGE);
    commandCharacteristic->notify();
}

// =========================================================
// CALLBACKS BLE
// =========================================================

class SocialLeynaServerCallbacks final : public BLEServerCallbacks {
public:
    void onConnect(BLEServer* server) override {
        (void)server;
        deviceConnected = true;
        advertisingRestartRequested = false;

        Serial.println();
        Serial.println("Telefono conectado por Bluetooth BLE.");
    }

    void onDisconnect(BLEServer* server) override {
        (void)server;
        deviceConnected = false;
        advertisingRestartRequested = true;

        invalidatePendingCommands();

        Serial.println();
        Serial.println("Telefono desconectado; salidas apagadas.");
    }
};

class SocialLeynaCommandCallbacks final : public BLECharacteristicCallbacks {
public:
    void onWrite(BLECharacteristic* characteristic) override {
        if (characteristic == nullptr) {
            return;
        }

        // Arduino-ESP32 3.x devuelve String. El uso de auto/c_str()/length()
        // tambien conserva compatibilidad con nucleos anteriores.
        const auto rawValue = characteristic->getValue();

        char command[16] = {};
        const size_t length = normalizeCommand(
            rawValue.c_str(),
            rawValue.length(),
            command,
            sizeof(command)
        );

        if (length == 0) {
            return;
        }

        if (strcmp(command, "PING") == 0) {
            Serial.println("PING recibido correctamente.");
            sendReadyResponse();
            return;
        }

        if (strcmp(command, "STOP") == 0) {
            invalidatePendingCommands();
            Serial.println("STOP recibido: salidas apagadas.");
            return;
        }

        const CommandType type = parseHapticCommand(command);
        if (type == CommandType::NONE) {
            Serial.print("Comando BLE desconocido: ");
            Serial.println(command);
            return;
        }

        if (!queueLatestCommand(type)) {
            Serial.println("ERROR: no se pudo registrar el comando BLE.");
        }
    }
};

// =========================================================
// INICIALIZACION Y PUBLICIDAD BLE
// =========================================================

bool startAdvertising() {
    if (bleAdvertising == nullptr) {
        return false;
    }

    const bool started = bleAdvertising->start();

    if (started) {
        Serial.println("Publicidad BLE activa.");
    } else {
        Serial.println("ERROR: no se pudo iniciar la publicidad BLE.");
    }

    return started;
}

bool initializeBluetooth() {
    Serial.println("Iniciando servidor Bluetooth BLE...");

    BLEDevice::init(BleConfig::DEVICE_NAME);

    bleServer = BLEDevice::createServer();
    if (bleServer == nullptr) {
        Serial.println("ERROR: no se pudo crear el servidor BLE.");
        return false;
    }

    bleServer->setCallbacks(new SocialLeynaServerCallbacks());

    BLEService* service =
        bleServer->createService(BleConfig::SERVICE_UUID);
    if (service == nullptr) {
        Serial.println("ERROR: no se pudo crear el servicio BLE.");
        return false;
    }

    commandCharacteristic = service->createCharacteristic(
        BleConfig::COMMAND_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_WRITE_NR |
        BLECharacteristic::PROPERTY_NOTIFY
    );

    if (commandCharacteristic == nullptr) {
        Serial.println("ERROR: no se pudo crear la caracteristica BLE.");
        return false;
    }

    commandCharacteristic->setCallbacks(
        new SocialLeynaCommandCallbacks()
    );
    commandCharacteristic->addDescriptor(new BLE2902());
    commandCharacteristic->setValue(BleConfig::READY_MESSAGE);

    service->start();

    bleAdvertising = BLEDevice::getAdvertising();
    if (bleAdvertising == nullptr) {
        Serial.println("ERROR: no se pudo obtener el objeto de publicidad BLE.");
        return false;
    }

    // El UUID queda en el anuncio principal para que ScanFilter lo encuentre.
    BLEAdvertisementData advertisementData;
    advertisementData.setFlags(BleConfig::ADVERTISING_FLAGS);
    advertisementData.setCompleteServices(
        BLEUUID(BleConfig::SERVICE_UUID)
    );

    // El nombre completo se coloca en scan response para no exceder el
    // espacio del anuncio BLE tradicional.
    BLEAdvertisementData scanResponseData;
    scanResponseData.setName(BleConfig::DEVICE_NAME);

    bleAdvertising->setAdvertisementData(advertisementData);
    bleAdvertising->setScanResponseData(scanResponseData);

    if (!startAdvertising()) {
        return false;
    }

    Serial.println();
    Serial.println("Bluetooth BLE iniciado correctamente.");
    Serial.print("Nombre: ");
    Serial.println(BleConfig::DEVICE_NAME);
    Serial.print("Servicio: ");
    Serial.println(BleConfig::SERVICE_UUID);
    Serial.print("Caracteristica: ");
    Serial.println(BleConfig::COMMAND_UUID);
    Serial.print("Memoria heap libre: ");
    Serial.print(ESP.getFreeHeap());
    Serial.println(" bytes");
    Serial.println("Esperando la conexion de la app...");

    return true;
}

// =========================================================
// SETUP Y LOOP
// =========================================================

void setup() {
    Serial.begin(115200);
    delay(600);

    pinMode(HardwareConfig::LED_PIN, OUTPUT);
    pinMode(HardwareConfig::HAPTIC_DRIVER_PIN, OUTPUT);

    outputsOff();

    // Longitud 1: el ultimo evento sustituye al anterior mediante overwrite.
    commandQueue = xQueueCreate(1, sizeof(QueuedCommand));

    Serial.println();
    Serial.println("============================================");
    Serial.println("VIBRAFIL ESP32 BLE - social_Leyna 3.0");
    Serial.println("============================================");
    Serial.print("Salida haptica (entrada del driver): GPIO");
    Serial.println(HardwareConfig::HAPTIC_DRIVER_PIN);
#if SOCIAL_LEYNA_ENABLE_AUDIO
    Serial.println("Excitadores PAM8403 GPIO25/26: ACTIVADOS");
#else
    Serial.println("Excitadores PAM8403: DESACTIVADOS para esta variante");
#endif

    if (commandQueue == nullptr) {
        Serial.println("ERROR CRITICO: no se pudo crear la cola FreeRTOS.");
        bleReady = false;
        return;
    }

    bleReady = initializeBluetooth();
}

void loop() {
    if (!bleReady) {
        // Senal visual de error de inicializacion sin bloquear el sistema.
        setLed(true);
        delay(120);
        setLed(false);
        delay(880);
        return;
    }

    if (deviceConnected && !previousDeviceConnected) {
        previousDeviceConnected = true;
        Serial.println("Conexion BLE establecida y servicio listo.");
    }

    if (!deviceConnected && previousDeviceConnected) {
        previousDeviceConnected = false;
    }

    if (!deviceConnected && advertisingRestartRequested) {
        advertisingRestartRequested = false;
        delay(250);
        startAdvertising();
        Serial.println("Esperando reconexion de la app...");
    }

    QueuedCommand command{};

    if (xQueueReceive(
            commandQueue,
            &command,
            pdMS_TO_TICKS(20)
        ) == pdPASS) {
        processCommand(command);
    }

    delay(5);
}
