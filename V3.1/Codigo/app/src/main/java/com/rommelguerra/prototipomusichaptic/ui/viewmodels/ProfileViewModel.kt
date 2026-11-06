package com.rommelguerra.prototipomusichaptic.ui.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.rommelguerra.prototipomusichaptic.data.BaseDatosHelper
import java.io.File
import java.io.FileOutputStream

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper = BaseDatosHelper(application)
    
    var nombre by mutableStateOf("")
    var apellido by mutableStateOf("")
    var correo by mutableStateOf("")
    var fotoPerfilUri by mutableStateOf<String?>(null)
    var fotoPortadaUri by mutableStateOf<String?>(null)
    var aciertos by mutableStateOf(0)
    var errores by mutableStateOf(0)
    var racha by mutableStateOf(0)

    init {
        loadProfile()
    }

    fun loadProfile() {
        try {
            val sharedPreferences = getApplication<Application>().getSharedPreferences("SesionUsuario", Context.MODE_PRIVATE)
            val email = sharedPreferences.getString("correoUsuario", "") ?: ""
            
            if (email.isNotEmpty()) {
                val datos = dbHelper.obtenerDatosPerfil(email)
                nombre = datos?.get("nombre") ?: "Usuario"
                apellido = datos?.get("apellido") ?: ""
                correo = datos?.get("correo") ?: email
                
                // Cargar URIs persistentes (rutas de archivos locales)
                fotoPerfilUri = datos?.get("foto_perfil")
                fotoPortadaUri = datos?.get("foto_portada")

                val idUser = dbHelper.obtenerIdUsuario(email)
                val progreso = dbHelper.obtenerResumenProgreso(idUser)
                aciertos = progreso["total_aciertos"] ?: 0
                errores = progreso["total_errores"] ?: 0
                racha = progreso["max_racha"] ?: 0
            }
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error cargando perfil", e)
        }
    }

    fun saveChanges(nuevoNombre: String, nuevoApellido: String, onResult: (Boolean) -> Unit) {
        try {
            val db = dbHelper.writableDatabase
            val values = android.content.ContentValues().apply {
                put(BaseDatosHelper.COL_NOMBRE, nuevoNombre)
                put(BaseDatosHelper.COL_APELLIDO, nuevoApellido)
                put(BaseDatosHelper.COL_FOTO_PERFIL, fotoPerfilUri)
                put(BaseDatosHelper.COL_FOTO_PORTADA, fotoPortadaUri)
            }
            val res = db.update(BaseDatosHelper.TABLA_USUARIOS, values, "${BaseDatosHelper.COL_CORREO} = ?", arrayOf(correo)) > 0
            if (res) {
                nombre = nuevoNombre
                apellido = nuevoApellido
            }
            onResult(res)
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error salvando cambios", e)
            onResult(false)
        }
    }

    /**
     * Copia una imagen externa a la memoria interna de la app para que sea persistente.
     */
    fun persistImage(uri: Uri, isProfile: Boolean) {
        try {
            val context = getApplication<Application>()
            val fileName = if (isProfile) "profile_${correo.replace("@", "_")}.jpg" else "cover_${correo.replace("@", "_")}.jpg"
            val file = File(context.filesDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (isProfile) {
                fotoPerfilUri = file.absolutePath
            } else {
                fotoPortadaUri = file.absolutePath
            }
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error persistiendo imagen", e)
        }
    }

    fun logout(onLogout: () -> Unit) {
        try {
            val sharedPreferences = getApplication<Application>().getSharedPreferences("SesionUsuario", Context.MODE_PRIVATE)
            sharedPreferences.edit().clear().apply()
            onLogout()
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error en logout", e)
        }
    }

    override fun onCleared() {
        try {
            dbHelper.close()
        } catch (e: Exception) {}
        super.onCleared()
    }
}
