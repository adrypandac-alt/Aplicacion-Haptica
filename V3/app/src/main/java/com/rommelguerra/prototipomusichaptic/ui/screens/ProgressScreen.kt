package com.rommelguerra.prototipomusichaptic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rommelguerra.prototipomusichaptic.ui.components.BottomNavBar
import com.rommelguerra.prototipomusichaptic.ui.navigation.Screen
import com.rommelguerra.prototipomusichaptic.ui.viewmodels.ProgressViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onNavigate: (String) -> Unit, 
    onBack: () -> Unit,
    viewModel: ProgressViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadProgress()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Progreso", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            BottomNavBar(currentScreen = "", onNavigate = onNavigate)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFDDDDDB)) // ColorFondo
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Rendimiento Global", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                        CircularProgressIndicator(
                            progress = { viewModel.porcentaje / 100f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 12.dp,
                            color = Color(0xFF00D166),
                            trackColor = Color.LightGray
                        )
                        Text(text = "${viewModel.porcentaje}%", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ProgressScoreItem("Aciertos", viewModel.aciertos.toString(), Color(0xFF2A703D))
                        ProgressScoreItem("Errores", viewModel.errores.toString(), Color(0xFFB32626))
                        ProgressScoreItem("Racha Máx", viewModel.rachaMax.toString(), Color(0xFFFFD700))
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressScoreItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
    }
}
