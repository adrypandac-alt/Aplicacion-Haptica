package com.rommelguerra.prototipomusichaptic.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.rommelguerra.prototipomusichaptic.data.BaseDatosHelper

class RegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper = BaseDatosHelper(application)

    var nombre by mutableStateOf("")
    var apellido by mutableStateOf("")
    var correo by mutableStateOf("")
    var contrasena by mutableStateOf("")
    var confirmarContrasena by mutableStateOf("")

    fun registrar(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val n = nombre.trim()
        val a = apellido.trim()
        val c = correo.trim()
        val p = contrasena
        val cp = confirmarContrasena

        if (n.isEmpty() || a.isEmpty() || c.isEmpty() || p.isEmpty() || !c.contains("@")) {
            onError("Campos incompletos o correo inválido")
            return
        }

        if (p != cp || p.length < 8) {
            onError("Contraseñas no coinciden o menor a 8 caracteres")
            return
        }

        if (dbHelper.verificarCorreoExiste(c)) {
            onError("El correo ya se encuentra registrado. Inicie sesión.")
            return
        }

        val exito = dbHelper.registrarUsuario(n, a, c, p)
        if (exito) {
            onSuccess()
        } else {
            onError("Error crítico al guardar en la Base de Datos")
        }
    }
}
