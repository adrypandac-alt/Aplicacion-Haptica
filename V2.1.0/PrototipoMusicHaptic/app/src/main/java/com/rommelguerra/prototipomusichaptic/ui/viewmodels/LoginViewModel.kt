package com.rommelguerra.prototipomusichaptic.ui.viewmodels

import android.app.Application
import android.content.Context
import android.os.CountDownTimer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.rommelguerra.prototipomusichaptic.data.BaseDatosHelper
import java.util.Locale

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper = BaseDatosHelper(application)
    private val sharedPreferences = application.getSharedPreferences("SesionUsuario", Context.MODE_PRIVATE)

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    
    var isBlocked by mutableStateOf(false)
    var countdownText by mutableStateOf("")
    var attemptsCount = 0

    fun login(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (isBlocked) {
            onError("Acceso bloqueado temporalmente.")
            return
        }

        if (email.isEmpty() || password.isEmpty() || !email.contains("@")) {
            onError("Ingrese credenciales válidas.")
            return
        }

        val isValid = dbHelper.verificarCredenciales(email, password)

        if (isValid) {
            sharedPreferences.edit().apply {
                putBoolean("isLoggedIn", true)
                putString("correoUsuario", email)
                apply()
            }
            attemptsCount = 0
            onSuccess()
        } else {
            attemptsCount++
            if (attemptsCount >= 3) {
                startBlockTimer()
                onError("Límite de intentos alcanzado. Bloqueado por 5 min.")
            } else {
                onError("Credenciales incorrectas. Intentos fallidos: $attemptsCount/3")
            }
        }
    }

    private fun startBlockTimer() {
        isBlocked = true
        object : CountDownTimer(300000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                countdownText = String.format(Locale.getDefault(), "Bloqueado por seguridad. Reintente en %02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                isBlocked = false
                attemptsCount = 0
                countdownText = ""
            }
        }.start()
    }
}
