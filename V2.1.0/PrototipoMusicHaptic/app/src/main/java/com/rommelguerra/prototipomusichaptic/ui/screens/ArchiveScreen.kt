package com.rommelguerra.prototipomusichaptic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rommelguerra.prototipomusichaptic.R
import com.rommelguerra.prototipomusichaptic.ui.components.BottomNavBar
import com.rommelguerra.prototipomusichaptic.ui.navigation.Screen

@Composable
fun ArchiveScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFDDDDDB)) // @color/color_fondo
    ) {
        Column {
            // Header exactly like activity_archive_mode.xml
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardReturn,
                            contentDescription = "Volver",
                            tint = Color.Black
                        )
                    }
                    Text(
                        text = "Modo Archivo",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = { onNavigate(Screen.Settings.route) },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ajustes),
                            contentDescription = "Ajustes",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Selecciona una pieza de la biblioteca o analiza tu propio archivo de audio.",
                    color = Color(0xFF8397A7), // @color/color_texto_secundario
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFE0E0E0))
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Piezas Fáciles
                SectionHeader(title = "Piezas Fáciles", dotColor = Color(0xFF2A703D))
                SongCard(
                    title = "Escala Ascendente / Descendente\n(Básico)",
                    onClick = { onNavigate("file_mode/Escala Mixta") }
                )
                SongCard(
                    title = "Twinkle Twinkle Little Star\n(Popular Infantil)",
                    onClick = { onNavigate("file_mode/Twinkle Twinkle Little Star") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Piezas Medias
                SectionHeader(title = "Piezas Medias", dotColor = Color(0xFFFFD700))
                SongCard(
                    title = "Fray Santiago\n(Canon Rítmico)",
                    onClick = { onNavigate("file_mode/Fray Santiago") }
                )
                SongCard(
                    title = "Oda a la Alegría\n(Beethoven)",
                    onClick = { onNavigate("file_mode/Oda a la Alegría") }
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Laboratorio de Audio Externo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = { onNavigate("file_mode/external") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF294B77)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Subir Audio Propio (.mp3 / .wav)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Nav
            BottomNavBar(currentScreen = Screen.Archive.route, onNavigate = onNavigate)
        }
    }
}

@Composable
fun SectionHeader(title: String, dotColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = title,
            modifier = Modifier.padding(start = 8.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.Black
        )
    }
}

@Composable
fun SongCard(title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 90.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = Color.Black,
                lineHeight = 22.sp
            )
        }
    }
}
