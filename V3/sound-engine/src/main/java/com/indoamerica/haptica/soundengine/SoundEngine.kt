package com.indoamerica.haptica.soundengine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

class SoundEngine(context: Context) : SoundRecognizer {

    companion object {
        const val SR = 16000
        const val WINDOW = 15600
        const val HOP = 7800
        const val EMB_DIM = 1024
        const val ANTIRREBOTE_MS = 3000L
        var CAL_OFFSET = 90f   // dB SPL = dBFS + CAL_OFFSET (calibrar por telefono)
    }

    private val clases: List<String> =
        context.assets.open("clases.txt").bufferedReader().readText()
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    private val config = ContextConfig(
        context.assets.open("contexts.json").bufferedReader().readText()
    )

    private fun cargar(context: Context, nombre: String): Interpreter {
        val bytes = context.assets.open(nombre).readBytes()
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buf.put(bytes); buf.rewind()
        return Interpreter(buf, Interpreter.Options().setNumThreads(2))
    }

    private val yamnet: Interpreter = cargar(context, "yamnet.tflite").also {
        it.resizeInput(0, intArrayOf(WINDOW))   // la entrada del modelo es dinamica
        it.allocateTensors()
    }
    private val head: Interpreter = cargar(context, "head.tflite")

    // Salida de YAMNet con forma [1, 1024] (el embedding), buscada en tiempo de ejecucion
    private val idxEmb: Int = (0 until yamnet.outputTensorCount).first {
        val s = yamnet.getOutputTensor(it).shape()
        s.size == 2 && s[1] == EMB_DIM
    }

    private val embOut = Array(1) { FloatArray(EMB_DIM) }
    private val probsOut = Array(1) { FloatArray(clases.size) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile private var contexto = "hogar"
    private var previa: String? = null
    private val ultima = HashMap<String, Long>()

    private val _events = MutableSharedFlow<SoundEvent>(extraBufferCapacity = 16)
    private val _log = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val events: Flow<SoundEvent> = _events
    override val log: Flow<String> = _log

    override val contextosDisponibles: List<String> = config.nombres


    override fun setContext(contexto: String) {
        require(contexto in config.nombres) {
            "Contexto desconocido: $contexto. Disponibles: ${config.nombres}"
        }
        this.contexto = contexto
        previa = null
        ultima.clear()
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT
            )
            val bufBytes = maxOf(minBuf, HOP * 4 * 4)
            val record = crearAudioRecord(bufBytes)
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                _log.emit("ERROR: no se pudo abrir el microfono")
                return@launch
            }
            _log.emit("Escuchando en contexto: $contexto")
            record.startRecording()

            val ventana = FloatArray(WINDOW)
            val chunk = FloatArray(HOP)
            var llenos = 0
            try {
                while (isActive) {
                    val n = record.read(chunk, 0, HOP, AudioRecord.READ_BLOCKING)
                    if (n != HOP) { _log.emit("ERROR de lectura de audio: $n"); break }
                    System.arraycopy(ventana, HOP, ventana, 0, WINDOW - HOP)
                    System.arraycopy(chunk, 0, ventana, WINDOW - HOP, HOP)
                    if (llenos < 2) { llenos++; continue }
                    procesar(ventana)
                }
            } finally {
                record.stop()
                record.release()
                _log.emit("Detenido")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun crearAudioRecord(bufBytes: Int): AudioRecord {
        fun crear(fuente: Int) = AudioRecord(
            fuente, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, bufBytes
        )
        val r = crear(MediaRecorder.AudioSource.UNPROCESSED)   // sin AGC, mejor para medir dB
        if (r.state == AudioRecord.STATE_INITIALIZED) return r
        r.release()
        return crear(MediaRecorder.AudioSource.MIC)
    }

    private fun procesar(ventana: FloatArray) {
        var suma = 0.0
        for (v in ventana) suma += (v * v).toDouble()
        val rms = sqrt(suma / ventana.size)
        val dbSpl = (20 * log10(rms + 1e-9)).toFloat() + CAL_OFFSET

        // 1) YAMNet: audio -> embedding (solo se pide esa salida)
        val salidas = HashMap<Int, Any>()
        salidas[idxEmb] = embOut
        yamnet.runForMultipleInputsOutputs(arrayOf<Any>(ventana), salidas)

        // 2) Cabeza: embedding -> probabilidades
        head.run(embOut, probsOut)

        val p = probsOut[0]
        var k = 0
        for (i in p.indices) if (p[i] > p[k]) k = i
        val nombre = clases[k]
        val conf = p[k]

        val ctx = contexto
        val regla = config.rule(ctx, nombre)
        var estado = ""

        if (regla != null && conf >= regla.umbral) {
            if (dbSpl < regla.nivelMinDb) {
                previa = null
                estado = "  (descartado: muy lejos)"
            } else {
                if (previa == nombre) {                       // confirmada en 2 ventanas
                    val ahora = System.currentTimeMillis()
                    if (ahora - (ultima[nombre] ?: 0L) > ANTIRREBOTE_MS) {
                        ultima[nombre] = ahora
                        estado = "  >>> ALERTA (prioridad ${regla.prioridad})"
                        _events.tryEmit(SoundEvent(nombre, conf, regla.prioridad, ctx, dbSpl, ahora))
                    }
                }
                previa = nombre
            }
        } else {
            previa = null
            if (nombre != "fondo") estado = "  (descartado por contexto/umbral)"
        }

        _log.tryEmit(
            String.format(Locale.US, "%5.1f dB  %-12s %.2f%s", dbSpl, nombre, conf, estado)
        )
    }

    override fun stop() { job?.cancel(); job = null }

    /** Integración: espera que AudioRecord y la inferencia terminen antes de cerrar los modelos. */
    suspend fun releaseAndJoin() {
        val actual = job
        actual?.cancelAndJoin()
        job = null
        scope.cancel()
        yamnet.close()
        head.close()
    }

    override fun release() {
        stop()
        scope.cancel()
        yamnet.close()
        head.close()
    }


}
