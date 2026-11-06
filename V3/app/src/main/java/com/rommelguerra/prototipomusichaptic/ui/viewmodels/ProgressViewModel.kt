package com.rommelguerra.prototipomusichaptic.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.rommelguerra.prototipomusichaptic.data.BaseDatosHelper

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper = BaseDatosHelper(application)
    
    var aciertos by mutableStateOf(0)
    var errores by mutableStateOf(0)
    var rachaMax by mutableStateOf(0)
    var porcentaje by mutableStateOf(0)

    init {
        loadProgress()
    }

    fun loadProgress() {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("SesionUsuario", Context.MODE_PRIVATE)
        val email = sharedPreferences.getString("correoUsuario", "") ?: ""
        
        if (email.isNotEmpty()) {
            val idUser = dbHelper.obtenerIdUsuario(email)
            val res = dbHelper.obtenerResumenProgreso(idUser)
            aciertos = res["total_aciertos"] ?: 0
            errores = res["total_errores"] ?: 0
            rachaMax = res["max_racha"] ?: 0
            
            val total = aciertos + errores
            porcentaje = if (total > 0) (aciertos * 100) / total else 0
        }
    }

    override fun onCleared() {
        dbHelper.close()
        super.onCleared()
    }
}
