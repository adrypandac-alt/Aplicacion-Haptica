package com.rommelguerra.prototipomusichaptic.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.rommelguerra.prototipomusichaptic.R
import com.rommelguerra.prototipomusichaptic.models.AppGlobals
import com.rommelguerra.prototipomusichaptic.models.NoteModel
import com.rommelguerra.prototipomusichaptic.services.HapticService
import com.rommelguerra.prototipomusichaptic.ui.components.CustomInputField
import com.rommelguerra.prototipomusichaptic.ui.theme.VerdePrimario
import com.rommelguerra.prototipomusichaptic.ui.viewmodels.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val hapticService = remember { HapticService(context) }
    var editNombre by remember { mutableStateOf(viewModel.nombre) }
    var editApellido by remember { mutableStateOf(viewModel.apellido) }
    var intensity by remember { mutableStateOf(AppGlobals.intensidadSeleccionada) }

    // Launchers for image picking
    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.persistImage(it, true) }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.persistImage(it, false) }
    }

    LaunchedEffect(viewModel.nombre, viewModel.apellido) {
        if (editNombre.isEmpty()) editNombre = viewModel.nombre
        if (editApellido.isEmpty()) editApellido = viewModel.apellido
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Configuración de Perfil", 
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", modifier = Modifier.size(32.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Cambiar foto Perfil", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .border(4.dp, Color(0xFFFF5252), CircleShape)
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .clickable { profilePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (viewModel.fotoPerfilUri != null) {
                    AsyncImage(
                        model = viewModel.fotoPerfilUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.usuario),
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = Color.Unspecified
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(text = "Cambiar nombre de usuario", modifier = Modifier.fillMaxWidth(), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            
            Spacer(modifier = Modifier.height(8.dp))

            CustomInputField(
                value = editNombre,
                onValueChange = { editNombre = it },
                placeholder = "Nombre"
            )
            Spacer(modifier = Modifier.height(8.dp))
            CustomInputField(
                value = editApellido,
                onValueChange = { editApellido = it },
                placeholder = "Apellido"
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Calibración y Prueba de Señal
            Text(text = "Prueba de Señal y Calibración", modifier = Modifier.fillMaxWidth(), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IntensityButtonSmall("BAJA", intensity == "BAJA") {
                    intensity = "BAJA"
                    AppGlobals.intensidadSeleccionada = "BAJA"
                }
                IntensityButtonSmall("MEDIA", intensity == "MEDIA") {
                    intensity = "MEDIA"
                    AppGlobals.intensidadSeleccionada = "MEDIA"
                }
                IntensityButtonSmall("ALTA", intensity == "ALTA") {
                    intensity = "ALTA"
                    AppGlobals.intensidadSeleccionada = "ALTA"
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    // Emitir una nota de prueba (ej: DO) para verificar la señal
                    val notaPrueba = NoteModel(
                        nombre = "DO",
                        descripcionVisual = "Prueba de señal",
                        patronVibracion = longArrayOf(0, 500),
                        amplitudes = intArrayOf(0, 255),
                        frecuenciaHz = 261.63,
                        ubicacion = 5.0f
                    )
                    try {
                        hapticService.emitirTacton(notaPrueba)
                        Toast.makeText(context, "Emitiendo señal de prueba...", Toast.LENGTH_SHORT).show()
                    } catch (e: SecurityException) {
                        Toast.makeText(context, "Faltan permisos de Bluetooth", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF294B77)), // Azul oscuro
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Probar Señal (Vibración/Audio)", color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(text = "Cambiar foto de portada", modifier = Modifier.fillMaxWidth(), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clickable { coverPicker.launch("image/*") },
                shape = RoundedCornerShape(12.dp)
            ) {
                if (viewModel.fotoPortadaUri != null) {
                    AsyncImage(
                        model = viewModel.fotoPortadaUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(VerdePrimario))
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { 
                    viewModel.saveChanges(editNombre, editApellido) { success ->
                        if (success) {
                            Toast.makeText(context, "Cambios guardados con éxito", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Error al guardar cambios", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Guardar Cambios", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.logout(onLogout) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VerdePrimario),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cerrar Sesión", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun IntensityButtonSmall(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(90.dp)
            .height(45.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF294B77) else Color.White,
            contentColor = if (isSelected) Color.White else Color.Black
        ),
        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray) else null,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
