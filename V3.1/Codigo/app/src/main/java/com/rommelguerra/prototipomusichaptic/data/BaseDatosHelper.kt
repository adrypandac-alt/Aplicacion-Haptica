package com.rommelguerra.prototipomusichaptic.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.rommelguerra.prototipomusichaptic.models.NoteModel
import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BaseDatosHelper(
    private val context: Context
) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {

        private const val DATABASE_NAME = "MusicHaptic.db"

        // IMPORTANTE:
        // Cambiamos de 11 a 12 para actualizar la BD.
        private const val DATABASE_VERSION = 12

        // =====================================================
        // USUARIOS
        // =====================================================

        const val TABLA_USUARIOS = "usuarios"

        const val COL_ID_USUARIO = "_id"
        const val COL_NOMBRE = "nombre"
        const val COL_APELLIDO = "apellido"
        const val COL_CORREO = "correo"
        const val COL_CONTRASENA = "contrasena"
        const val COL_FOTO_PERFIL = "foto_perfil"
        const val COL_FOTO_PORTADA = "foto_portada"

        // =====================================================
        // HISTORIAL
        // =====================================================

        const val TABLA_HISTORIAL =
            "historial_reproduccion"

        const val COL_ID_HISTORIAL = "_id"
        const val COL_HISTORIAL_NOMBRE = "nombre_archivo"
        const val COL_HISTORIAL_USER_ID = "id_usuario"
        const val COL_HISTORIAL_FECHA = "fecha_hora"
        const val COL_HISTORIAL_JSON = "trama_json"

        // =====================================================
        // DICCIONARIO DE NOTAS
        // =====================================================

        const val TABLA_DICCIONARIO =
            "diccionario_frecuencias"

        const val COL_ID_FRECUENCIA = "_id"

        const val COL_FREC_HISTORIAL_ID =
            "id_historial"

        const val COL_FREC_NOTA =
            "nota_musical"

        const val COL_FREC_HZ =
            "frecuencia_hz"

        const val COL_FREC_UBICACION =
            "ubicacion_espacial"

        // NUEVO
        const val COL_FREC_AMPLITUD =
            "amplitud"

        // NUEVO
        const val COL_FREC_DURACION =
            "duracion_ms"

        // =====================================================
        // PROGRESO
        // =====================================================

        const val TABLA_PROGRESO =
            "progreso_aprendizaje"

        const val COL_PROG_ID = "_id"
        const val COL_PROG_USER_ID = "id_usuario"
        const val COL_PROG_ACIERTOS = "aciertos"
        const val COL_PROG_ERRORES = "errores"
        const val COL_PROG_RACHA = "racha_maxima"
        const val COL_PROG_FECHA = "fecha_log"
    }

    // =========================================================
    // CREACIÓN DE LA BASE DE DATOS
    // =========================================================

    override fun onCreate(
        db: SQLiteDatabase
    ) {

        // -----------------------------------------------------
        // TABLA USUARIOS
        // -----------------------------------------------------

        db.execSQL(
            """
            CREATE TABLE $TABLA_USUARIOS (
                $COL_ID_USUARIO INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NOMBRE TEXT,
                $COL_APELLIDO TEXT,
                $COL_CORREO TEXT UNIQUE NOT NULL,
                $COL_CONTRASENA TEXT NOT NULL,
                $COL_FOTO_PERFIL TEXT,
                $COL_FOTO_PORTADA TEXT
            )
            """.trimIndent()
        )

        // -----------------------------------------------------
        // TABLA HISTORIAL
        // -----------------------------------------------------

        db.execSQL(
            """
            CREATE TABLE $TABLA_HISTORIAL (
                $COL_ID_HISTORIAL INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_HISTORIAL_NOMBRE TEXT NOT NULL,
                $COL_HISTORIAL_USER_ID INTEGER,
                $COL_HISTORIAL_FECHA TEXT NOT NULL,
                $COL_HISTORIAL_JSON TEXT,

                FOREIGN KEY($COL_HISTORIAL_USER_ID)
                REFERENCES $TABLA_USUARIOS($COL_ID_USUARIO)
            )
            """.trimIndent()
        )

        // -----------------------------------------------------
        // TABLA DE NOTAS
        // -----------------------------------------------------

        db.execSQL(
            """
            CREATE TABLE $TABLA_DICCIONARIO (
                $COL_ID_FRECUENCIA INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FREC_HISTORIAL_ID INTEGER,
                $COL_FREC_NOTA TEXT NOT NULL,
                $COL_FREC_HZ REAL NOT NULL,
                $COL_FREC_UBICACION REAL NOT NULL,
                $COL_FREC_AMPLITUD INTEGER NOT NULL DEFAULT 100,
                $COL_FREC_DURACION INTEGER NOT NULL DEFAULT 300,

                FOREIGN KEY($COL_FREC_HISTORIAL_ID)
                REFERENCES $TABLA_HISTORIAL($COL_ID_HISTORIAL)
            )
            """.trimIndent()
        )

        // -----------------------------------------------------
        // TABLA PROGRESO
        // -----------------------------------------------------

        db.execSQL(
            """
            CREATE TABLE $TABLA_PROGRESO (
                $COL_PROG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PROG_USER_ID INTEGER,
                $COL_PROG_ACIERTOS INTEGER,
                $COL_PROG_ERRORES INTEGER,
                $COL_PROG_RACHA INTEGER,
                $COL_PROG_FECHA TEXT,

                FOREIGN KEY($COL_PROG_USER_ID)
                REFERENCES $TABLA_USUARIOS($COL_ID_USUARIO)
            )
            """.trimIndent()
        )

        // Cargar automáticamente las 12 notas.
        insertarNotasDesdeJSON(db)
    }

    // =========================================================
    // ACTUALIZACIÓN DE BD
    // =========================================================

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        /*
         * Conservamos el comportamiento de tu proyecto actual:
         * al cambiar la versión se reconstruyen las tablas.
         */

        db.execSQL(
            "DROP TABLE IF EXISTS $TABLA_PROGRESO"
        )

        db.execSQL(
            "DROP TABLE IF EXISTS $TABLA_DICCIONARIO"
        )

        db.execSQL(
            "DROP TABLE IF EXISTS $TABLA_HISTORIAL"
        )

        db.execSQL(
            "DROP TABLE IF EXISTS $TABLA_USUARIOS"
        )

        onCreate(db)
    }

    // =========================================================
    // CARGAR LAS 12 NOTAS DESDE JSON
    // =========================================================

    private fun insertarNotasDesdeJSON(
        db: SQLiteDatabase
    ) {

        try {

            val inputStream: InputStream =
                context.assets.open(
                    "tabla_triple_codificacion.json"
                )

            val size = inputStream.available()

            val buffer =
                ByteArray(size)

            inputStream.read(buffer)
            inputStream.close()

            val json =
                String(
                    buffer,
                    Charset.forName("UTF-8")
                )

            val jsonObject =
                JSONObject(json)

            val escalaCromatica =
                jsonObject.getJSONArray(
                    "escala_cromatica"
                )

            // -------------------------------------------------
            // Guardamos el JSON original
            // -------------------------------------------------

            val valuesHistorial =
                ContentValues().apply {

                    put(
                        COL_HISTORIAL_NOMBRE,
                        "SISTEMA_BASE"
                    )

                    put(
                        COL_HISTORIAL_USER_ID,
                        0
                    )

                    put(
                        COL_HISTORIAL_FECHA,
                        "2024-01-01 00:00:00"
                    )

                    put(
                        COL_HISTORIAL_JSON,
                        escalaCromatica.toString()
                    )
                }

            val idBase =
                db.insert(
                    TABLA_HISTORIAL,
                    null,
                    valuesHistorial
                )

            // -------------------------------------------------
            // Insertar cada nota
            // -------------------------------------------------

            for (
            i in 0 until escalaCromatica.length()
            ) {

                val item =
                    escalaCromatica.getJSONObject(i)

                val values =
                    ContentValues().apply {

                        put(
                            COL_FREC_HISTORIAL_ID,
                            idBase
                        )

                        put(
                            COL_FREC_NOTA,
                            item.getString("nota")
                        )

                        put(
                            COL_FREC_HZ,
                            item.getDouble(
                                "frecuencia_hz"
                            )
                        )

                        put(
                            COL_FREC_UBICACION,
                            item.getDouble(
                                "ubicacion_espacial"
                            )
                        )

                        /*
                         * Si el JSON no tiene amplitud,
                         * usamos 100 %.
                         */
                        put(
                            COL_FREC_AMPLITUD,
                            item.optInt(
                                "amplitud_sugerida",
                                100
                            )
                        )

                        /*
                         * Si el JSON no tiene duración,
                         * usamos 300 ms.
                         */
                        put(
                            COL_FREC_DURACION,
                            item.optInt(
                                "duracion_ms",
                                300
                            )
                        )
                    }

                db.insert(
                    TABLA_DICCIONARIO,
                    null,
                    values
                )
            }

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }

    // =========================================================
    // OBTENER LAS 12 NOTAS
    // =========================================================

    fun obtenerNotasMuestrario():
            List<NoteModel> {

        val lista =
            mutableListOf<NoteModel>()

        val db =
            this.readableDatabase

        val cursor =
            db.rawQuery(
                """
                SELECT *
                FROM $TABLA_DICCIONARIO
                WHERE $COL_FREC_HISTORIAL_ID = (
                    SELECT $COL_ID_HISTORIAL
                    FROM $TABLA_HISTORIAL
                    WHERE $COL_HISTORIAL_NOMBRE = 'SISTEMA_BASE'
                )
                ORDER BY $COL_ID_FRECUENCIA ASC
                """.trimIndent(),
                null
            )

        if (cursor.moveToFirst()) {

            do {

                val nombre =
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            COL_FREC_NOTA
                        )
                    )

                val frecuencia =
                    cursor.getDouble(
                        cursor.getColumnIndexOrThrow(
                            COL_FREC_HZ
                        )
                    )

                val ubicacion =
                    cursor.getDouble(
                        cursor.getColumnIndexOrThrow(
                            COL_FREC_UBICACION
                        )
                    ).toFloat()

                val amplitud =
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            COL_FREC_AMPLITUD
                        )
                    )

                val duracion =
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            COL_FREC_DURACION
                        )
                    )

                val nota =
                    NoteModel(

                        nombre = nombre,

                        descripcionVisual =
                            "Nota $nombre - " +
                                    String.format(
                                        Locale.US,
                                        "%.2f Hz",
                                        frecuencia
                                    ),

                        patronVibracion =
                            longArrayOf(
                                0L,
                                duracion.toLong()
                            ),

                        amplitudes =
                            intArrayOf(
                                0,
                                255
                            ),

                        frecuenciaHz =
                            frecuencia,

                        ubicacion =
                            ubicacion,

                        amplitudBrazalete =
                            amplitud,

                        duracionMs =
                            duracion
                    )

                lista.add(nota)

            } while (
                cursor.moveToNext()
            )
        }

        cursor.close()

        return lista
    }

    // =========================================================
    // BUSCAR UNA NOTA
    // =========================================================

    fun obtenerNotaPorNombre(
        nombreNota: String
    ): NoteModel? {

        return obtenerNotasMuestrario()
            .firstOrNull {

                it.nombre.equals(
                    nombreNota,
                    ignoreCase = true
                )
            }
    }

    // =========================================================
    // USUARIOS
    // =========================================================

    fun registrarUsuario(
        nombre: String,
        apellido: String,
        correo: String,
        contrasena: String
    ): Boolean {

        val db =
            this.writableDatabase

        val values =
            ContentValues().apply {

                put(
                    COL_NOMBRE,
                    nombre
                )

                put(
                    COL_APELLIDO,
                    apellido
                )

                put(
                    COL_CORREO,
                    correo.trim().lowercase()
                )

                put(
                    COL_CONTRASENA,
                    contrasena
                )
            }

        return db.insert(
            TABLA_USUARIOS,
            null,
            values
        ) != -1L
    }

    fun verificarCorreoExiste(
        correo: String
    ): Boolean {

        val db =
            this.readableDatabase

        val cursor =
            db.rawQuery(
                """
                SELECT 1
                FROM $TABLA_USUARIOS
                WHERE $COL_CORREO = ?
                """.trimIndent(),
                arrayOf(
                    correo.trim().lowercase()
                )
            )

        val existe =
            cursor.count > 0

        cursor.close()

        return existe
    }

    fun verificarCredenciales(
        correo: String,
        contrasena: String
    ): Boolean {

        val db =
            this.readableDatabase

        val cursor =
            db.rawQuery(
                """
                SELECT 1
                FROM $TABLA_USUARIOS
                WHERE $COL_CORREO = ?
                AND $COL_CONTRASENA = ?
                """.trimIndent(),
                arrayOf(
                    correo.trim().lowercase(),
                    contrasena
                )
            )

        val existe =
            cursor.count > 0

        cursor.close()

        return existe
    }

    // =========================================================
    // PERFIL
    // =========================================================

    fun obtenerDatosPerfil(
        correo: String
    ): Map<String, String>? {

        val db =
            this.readableDatabase

        val cursor =
            db.rawQuery(
                """
                SELECT
                    $COL_NOMBRE,
                    $COL_APELLIDO,
                    $COL_FOTO_PERFIL,
                    $COL_FOTO_PORTADA
                FROM $TABLA_USUARIOS
                WHERE $COL_CORREO = ?
                """.trimIndent(),
                arrayOf(
                    correo.trim().lowercase()
                )
            )

        var datos:
                Map<String, String>? = null

        if (cursor.moveToFirst()) {

            datos =
                mapOf(

                    "nombre" to
                            (cursor.getString(0) ?: ""),

                    "apellido" to
                            (cursor.getString(1) ?: ""),

                    "foto_perfil" to
                            (cursor.getString(2) ?: ""),

                    "foto_portada" to
                            (cursor.getString(3) ?: "")
                )
        }

        cursor.close()

        return datos
    }

    fun actualizarFotosPerfil(
        correo: String,
        rutaPerfil: String?,
        rutaPortada: String?
    ): Boolean {

        val db =
            this.writableDatabase

        val values =
            ContentValues()

        if (rutaPerfil != null) {

            values.put(
                COL_FOTO_PERFIL,
                rutaPerfil
            )
        }

        if (rutaPortada != null) {

            values.put(
                COL_FOTO_PORTADA,
                rutaPortada
            )
        }

        if (values.size() == 0) {
            return true
        }

        return db.update(
            TABLA_USUARIOS,
            values,
            "$COL_CORREO = ?",
            arrayOf(
                correo.trim().lowercase()
            )
        ) > 0
    }

    // =========================================================
    // PROGRESO
    // =========================================================

    fun obtenerResumenProgreso(
        idUser: Int
    ): Map<String, Int> {

        val db =
            this.readableDatabase

        val cursor =
            db.rawQuery(
                """
                SELECT
                    SUM($COL_PROG_ACIERTOS),
                    SUM($COL_PROG_ERRORES),
                    MAX($COL_PROG_RACHA)
                FROM $TABLA_PROGRESO
                WHERE $COL_PROG_USER_ID = ?
                """.trimIndent(),
                arrayOf(
                    idUser.toString()
                )
            )

        var resultado =
            mapOf(
                "total_aciertos" to 0,
                "total_errores" to 0,
                "max_racha" to 0
            )

        if (cursor.moveToFirst()) {

            resultado =
                mapOf(

                    "total_aciertos" to
                            cursor.getInt(0),

                    "total_errores" to
                            cursor.getInt(1),

                    "max_racha" to
                            cursor.getInt(2)
                )
        }

        cursor.close()

        return resultado
    }

    fun obtenerIdUsuario(
        correo: String
    ): Int {

        val db =
            this.readableDatabase

        val cursor =
            db.rawQuery(
                """
                SELECT $COL_ID_USUARIO
                FROM $TABLA_USUARIOS
                WHERE $COL_CORREO = ?
                """.trimIndent(),
                arrayOf(
                    correo.trim().lowercase()
                )
            )

        var id = -1

        if (cursor.moveToFirst()) {

            id =
                cursor.getInt(0)
        }

        cursor.close()

        return id
    }

    fun guardarProgreso(
        idUser: Int,
        aciertos: Int,
        errores: Int,
        racha: Int
    ) {

        val db =
            this.writableDatabase

        val values =
            ContentValues().apply {

                put(
                    COL_PROG_USER_ID,
                    idUser
                )

                put(
                    COL_PROG_ACIERTOS,
                    aciertos
                )

                put(
                    COL_PROG_ERRORES,
                    errores
                )

                put(
                    COL_PROG_RACHA,
                    racha
                )

                put(
                    COL_PROG_FECHA,
                    fechaActual()
                )
            }

        db.insert(
            TABLA_PROGRESO,
            null,
            values
        )
    }

    fun obtenerMejorProgreso(
        idUser: Int
    ): Map<String, Int>? {

        val db =
            this.readableDatabase

        val cursor =
            db.rawQuery(
                """
                SELECT
                    MAX($COL_PROG_ACIERTOS),
                    MAX($COL_PROG_RACHA)
                FROM $TABLA_PROGRESO
                WHERE $COL_PROG_USER_ID = ?
                """.trimIndent(),
                arrayOf(
                    idUser.toString()
                )
            )

        var resultado:
                Map<String, Int>? = null

        if (cursor.moveToFirst()) {

            resultado =
                mapOf(

                    "max_aciertos" to
                            cursor.getInt(0),

                    "max_racha" to
                            cursor.getInt(1)
                )
        }

        cursor.close()

        return resultado
    }

    // =========================================================
    // CANCIONES / ANÁLISIS
    // =========================================================

    fun registrarCancionConAnalisis(
        nombre: String,
        idUser: Int,
        tramaJson: String
    ) {

        val db =
            this.writableDatabase

        val check =
            db.rawQuery(
                """
                SELECT $COL_ID_HISTORIAL
                FROM $TABLA_HISTORIAL
                WHERE $COL_HISTORIAL_NOMBRE = ?
                AND $COL_HISTORIAL_USER_ID = ?
                """.trimIndent(),
                arrayOf(
                    nombre,
                    idUser.toString()
                )
            )

        if (check.count > 0) {

            check.close()

            return
        }

        check.close()

        val values =
            ContentValues().apply {

                put(
                    COL_HISTORIAL_NOMBRE,
                    nombre
                )

                put(
                    COL_HISTORIAL_USER_ID,
                    idUser
                )

                put(
                    COL_HISTORIAL_FECHA,
                    fechaActual()
                )

                put(
                    COL_HISTORIAL_JSON,
                    tramaJson
                )
            }

        db.insert(
            TABLA_HISTORIAL,
            null,
            values
        )
    }

    fun obtenerTramaAnalizada(
        nombre: String,
        idUser: Int
    ): String? {

        val db =
            this.readableDatabase

        val cursor =
            db.rawQuery(
                """
                SELECT $COL_HISTORIAL_JSON
                FROM $TABLA_HISTORIAL
                WHERE $COL_HISTORIAL_NOMBRE = ?
                AND $COL_HISTORIAL_USER_ID = ?
                """.trimIndent(),
                arrayOf(
                    nombre,
                    idUser.toString()
                )
            )

        var resultado: String? = null

        if (cursor.moveToFirst()) {

            resultado =
                cursor.getString(0)
        }

        cursor.close()

        return resultado
    }

    // =========================================================
    // ESPACIALIDAD
    // =========================================================

    /**
     * Conservamos esta función porque HapticService
     * actualmente la utiliza.
     *
     * Sin embargo, la espacialidad precisa será enviada
     * posteriormente al ESP32 usando nota.ubicacion.
     */
    fun obtenerConfiguracionRastro(
        nombreNota: String
    ): Map<String, String>? {

        val nota =
            obtenerNotaPorNombre(
                nombreNota
            )

        if (nota != null) {

            val motor =
                when {

                    nota.ubicacion < 4.0f ->
                        "IZQ"

                    nota.ubicacion > 6.0f ->
                        "DER"

                    else ->
                        "AMBOS"
                }

            return mapOf(
                "motor_inicio" to motor,
                "motor_destino" to motor
            )
        }

        return null
    }

    // =========================================================
    // UTILIDAD
    // =========================================================

    private fun fechaActual(): String {

        return SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(
            Date()
        )
    }
}