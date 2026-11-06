package com.rommelguerra.prototipomusichaptic.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rommelguerra.prototipomusichaptic.ui.navigation.Screen
import com.rommelguerra.prototipomusichaptic.ui.components.BottomNavBar
import com.rommelguerra.prototipomusichaptic.ui.theme.VerdePrimario
import com.rommelguerra.prototipomusichaptic.ui.viewmodels.FileModeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileModeScreen(
    songName: String?,
    onBack: () -> Unit,
    viewModel: FileModeViewModel = viewModel()
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val cacheDir = context.cacheDir

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.analyzeAudioFile(it, contentResolver, cacheDir) }
    }

    LaunchedEffect(songName) {
        if (songName != null && songName != "external") {
            viewModel.loadFromLibrary(songName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (songName == "external") "Analizador Háptico" else "Reproductor Musical", 
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
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
        },
        bottomBar = {
            BottomNavBar(currentScreen = Screen.Archive.route, onNavigate = { /* Logic in AppNavigation */ })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (songName == "external") {
                Text(
                    text = "Carga un archivo de música para mapear y traducir automáticamente sus notas a vibraciones hápticas.",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Black Card with Green Folder
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = Color(0xFF00D166)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = if (viewModel.fileName == "Ninguno") "Ningún archivo seleccionado" else viewModel.fileName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = { filePickerLauncher.launch("audio/*") },
                            modifier = Modifier
                                .width(220.dp)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerdePrimario),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            Text("Seleccionar Audio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                // Library Mode UI
                Text(
                    text = "Reproduciendo pieza de la biblioteca musical.",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(64.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = viewModel.fileName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = viewModel.statusText,
                fontSize = 18.sp,
                color = Color.Black,
                fontWeight = FontWeight.Medium
            )

            if (viewModel.isAnalyzing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    color = VerdePrimario
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Play / Stop Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Button
                Button(
                    onClick = { viewModel.playSequence() },
                    enabled = viewModel.canPlay && !viewModel.isPlaying,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D166)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(40.dp), tint = Color.White)
                }

                // Stop/Pause Button
                Button(
                    onClick = { viewModel.stopSequence() },
                    enabled = viewModel.isPlaying,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(40.dp), tint = Color.White)
                }
            }
        }
    }
}
