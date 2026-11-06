package com.rommelguerra.prototipomusichaptic.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rommelguerra.prototipomusichaptic.ui.theme.VerdePrimario
import com.rommelguerra.prototipomusichaptic.ui.viewmodels.LoginViewModel
import com.rommelguerra.prototipomusichaptic.ui.components.CustomInputField

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            Text(
                text = "Bienvenido",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Text(
                text = "Iniciar sesión para continuar",
                fontSize = 16.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Email Field
            Text(
                text = "Ingresa tu correo",
                fontSize = 14.sp,
                color = Color.Black,
                modifier = Modifier.fillMaxWidth()
            )
            
            CustomInputField(
                value = viewModel.email,
                onValueChange = { viewModel.email = it },
                placeholder = "Joselyn12002@hotmail.com",
                icon = { Icon(painterResource(id = android.R.drawable.ic_menu_myplaces), contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !viewModel.isBlocked
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            Text(
                text = "Ingresa tu contraseña",
                fontSize = 14.sp,
                color = Color.Black,
                modifier = Modifier.fillMaxWidth()
            )
            
            CustomInputField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it },
                placeholder = "A.1234567890",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isPassword = true,
                enabled = !viewModel.isBlocked
            )

            if (viewModel.isBlocked) {
                Text(
                    text = viewModel.countdownText,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.login(
                        onSuccess = {
                            Toast.makeText(context, "¡Inicio de sesión correcto!", Toast.LENGTH_SHORT).show()
                            onLoginSuccess()
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !viewModel.isBlocked,
                colors = ButtonDefaults.buttonColors(containerColor = VerdePrimario),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Continuar", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "¿No tienes una cuenta?",
                fontSize = 14.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNavigateToRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VerdePrimario),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VerdePrimario)
            ) {
                Text("Regístrate", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "𝄞",
            color = VerdePrimario,
            fontSize = 120.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}
