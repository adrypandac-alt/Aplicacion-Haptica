package com.rommelguerra.prototipomusichaptic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rommelguerra.prototipomusichaptic.R
import com.rommelguerra.prototipomusichaptic.ui.navigation.Screen
import com.rommelguerra.prototipomusichaptic.ui.theme.VerdePrimario

@Composable
fun BottomNavBar(currentScreen: String, onNavigate: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(VerdePrimario),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavIcon(Screen.MainMenu, R.drawable.chimenea_de_la_casa, "Inicio", currentScreen == Screen.MainMenu.route, onNavigate)
            NavIcon(Screen.Archive, R.drawable.carpeta_abierta, "Archivo", currentScreen == Screen.Archive.route, onNavigate)
            NavIcon(Screen.Profile, R.drawable.usuario, "Perfil", currentScreen == Screen.Profile.route, onNavigate)
        }
    }
}

@Composable
fun NavIcon(screen: Screen, iconRes: Int, label: String, isSelected: Boolean, onNavigate: (String) -> Unit) {
    val contentColor = if (isSelected) Color.White else Color.Black.copy(alpha = 0.6f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onNavigate(screen.route) }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(28.dp)
        )
        Text(text = label, fontSize = 11.sp, color = contentColor)
    }
}

@Composable
fun CustomInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: @Composable () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isPassword: Boolean = false,
    enabled: Boolean = true
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(top = 4.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(text = placeholder, color = Color.Gray, fontSize = 14.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
                    keyboardOptions = keyboardOptions,
                    visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    enabled = enabled,
                    cursorBrush = SolidColor(Color.Black)
                )
            }
            if (isPassword) {
                IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Ocultar" else "Ver",
                        tint = Color.Black
                    )
                }
            } else {
                icon()
            }
        }
    }
}

