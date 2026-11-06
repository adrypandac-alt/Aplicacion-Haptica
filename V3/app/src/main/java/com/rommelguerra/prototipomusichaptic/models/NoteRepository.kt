package com.rommelguerra.prototipomusichaptic.models

import com.rommelguerra.prototipomusichaptic.data.BaseDatosHelper

/**
 * Repositorio centralizado de las 12 notas musicales
 * utilizadas por Haptic Music.
 *
 * Cada nota contiene:
 * - Frecuencia vibrotáctil.
 * - Ubicación espacial.
 * - Amplitud.
 * - Duración.
 */
object NoteRepository {

    // Duración estándar de cada nota
    private const val DURACION_NOTA_MS = 300

    // Amplitud estándar del brazalete (porcentaje)
    private const val AMPLITUD_BRAZALETE = 100

    /**
     * Escala cromática completa de 12 notas.
     *
     * Ubicación:
     * 0.0  = izquierda
     * 5.0  = centro
     * 10.0 = derecha
     */
    val notasMusicales: List<NoteModel> = listOf(

        // 1. DO
        NoteModel(
            nombre = "DO",
            descripcionVisual = "Do - 62.15 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 62.147964530,
            ubicacion = 0.0f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        ),

        // 2. DO#
        NoteModel(
            nombre = "DO#",
            descripcionVisual = "Do# - 75.82 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 75.819517727,
            ubicacion = 0.9f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        ),

        // 3. RE
        NoteModel(
            nombre = "RE",
            descripcionVisual = "Re - 92.50 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 92.499811627,
            ubicacion = 1.8f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        ),

        // 4. RE#
        NoteModel(
            nombre = "RE#",
            descripcionVisual = "Re# - 112.85 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 112.849771185,
            ubicacion = 2.7f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        ),

        // 5. MI
        NoteModel(
            nombre = "MI",
            descripcionVisual = "Mi - 137.68 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 137.676721846,
            ubicacion = 3.6f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        ),

        // 6. FA
        NoteModel(
            nombre = "FA",
            descripcionVisual = "Fa - 167.97 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 167.965600644,
            ubicacion = 4.5f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        ),

        // 7. FA#
        NoteModel(
            nombre = "FA#",
            descripcionVisual = "Fa# - 204.92 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 204.918032787,
            ubicacion = 5.4f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        ),

        // 8. SOL
        NoteModel(
            nombre = "SOL",
            descripcionVisual = "Sol - 250.00 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 250.000000000,
            ubicacion = 6.3f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        ),

        // 9. SOL#
        NoteModel(
            nombre = "SOL#",
            descripcionVisual = "Sol# - 305.00 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 305.000000000,
            ubicacion = 7.2f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        ),

        // 10. LA
        NoteModel(
            nombre = "LA",
            descripcionVisual = "La - 372.10 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 372.100000000,
            ubicacion = 8.1f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        ),

        // 11. LA#
        NoteModel(
            nombre = "LA#",
            descripcionVisual = "La# - 453.96 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 453.962000000,
            ubicacion = 9.0f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        ),

        // 12. SI
        NoteModel(
            nombre = "SI",
            descripcionVisual = "Si - 553.83 Hz",
            patronVibracion = longArrayOf(
                0L,
                DURACION_NOTA_MS.toLong()
            ),
            amplitudes = intArrayOf(
                0,
                255
            ),
            frecuenciaHz = 553.833640000,
            ubicacion = 10.0f,
            amplitudBrazalete = AMPLITUD_BRAZALETE,
            duracionMs = DURACION_NOTA_MS
        )
    )

    /**
     * Busca una nota por nombre.
     *
     * Ejemplos:
     * DO
     * DO#
     * RE
     * SOL#
     */
    fun obtenerNotaPorNombre(
        nombre: String
    ): NoteModel? {

        return notasMusicales.find {
            it.nombre.equals(
                nombre,
                ignoreCase = true
            )
        }
    }

    /**
     * Obtiene las notas correspondientes
     * a una canción.
     */
    fun obtenerSecuenciaCancion(
        nombreCancion: String,
        dbHelper: BaseDatosHelper? = null
    ): List<NoteModel> {

        val notasLetras = when (nombreCancion) {

            "Twinkle Twinkle Little Star" -> listOf(
                "DO", "DO",
                "SOL", "SOL",
                "LA", "LA",
                "SOL",
                "FA", "FA",
                "MI", "MI",
                "RE", "RE",
                "DO"
            )

            "Oda a la Alegría" -> listOf(
                "MI", "MI",
                "FA", "SOL",
                "SOL", "FA",
                "MI", "RE",
                "DO", "DO",
                "RE", "MI",
                "MI", "RE",
                "RE"
            )

            "Fray Santiago" -> listOf(
                "DO", "RE",
                "MI", "DO",
                "DO", "RE",
                "MI", "DO",
                "MI", "FA",
                "SOL",
                "MI", "FA",
                "SOL"
            )

            "Escala Mixta" -> listOf(
                "DO",
                "DO#",
                "RE",
                "RE#",
                "MI",
                "FA",
                "FA#",
                "SOL",
                "SOL#",
                "LA",
                "LA#",
                "SI",

                "SI",
                "LA#",
                "LA",
                "SOL#",
                "SOL",
                "FA#",
                "FA",
                "MI",
                "RE#",
                "RE",
                "DO#",
                "DO"
            )

            else -> listOf(
                "DO",
                "DO#",
                "RE",
                "RE#",
                "MI",
                "FA",
                "FA#",
                "SOL",
                "SOL#",
                "LA",
                "LA#",
                "SI"
            )
        }

        /*
         * Si existe BaseDatosHelper,
         * utilizamos primero las notas de SQLite.
         *
         * Esto mantiene sincronizada la aplicación
         * con tabla_triple_codificacion.json.
         */
        if (dbHelper != null) {

            val notasBD =
                dbHelper.obtenerNotasMuestrario()

            return notasLetras.mapNotNull { nombre ->

                notasBD.find {

                    it.nombre.equals(
                        nombre,
                        ignoreCase = true
                    )
                }
            }
        }

        /*
         * Si no se proporciona BD,
         * utilizamos el repositorio local.
         */
        return notasLetras.mapNotNull { nombre ->

            obtenerNotaPorNombre(nombre)
        }
    }
}