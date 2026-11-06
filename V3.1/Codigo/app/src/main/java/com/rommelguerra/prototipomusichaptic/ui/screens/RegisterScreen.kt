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
import com.rommelguerra.prototipomusichaptic.ui.viewmodels.RegisterViewModel
import com.rommelguerra.prototipomusichaptic.ui.components.CustomInputField

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: RegisterViewModel = viewModel()
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
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Crear Cuenta",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Text(
                text = "Crea tu cuenta para continuar",
                fontSize = 16.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Nombre Field
            Text(text = "Ingresa tu nombre", fontSize = 14.sp, color = Color.Black, modifier = Modifier.fillMaxWidth())
            CustomInputField(
                value = viewModel.nombre,
                onValueChange = { viewModel.nombre = it },
                placeholder = "Nombre"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Apellido Field
            Text(text = "Ingresa tu apellido", fontSize = 14.sp, color = Color.Black, modifier = Modifier.fillMaxWidth())
            CustomInputField(
                value = viewModel.apellido,
                onValueChange = { viewModel.apellido = it },
                placeholder = "Apellido"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Correo Field
            Text(text = "Ingresa tu correo", fontSize = 14.sp, color = Color.Black, modifier = Modifier.fillMaxWidth())
            CustomInputField(
                value = viewModel.correo,
                onValueChange = { viewModel.correo = it },
                placeholder = "tu@correo.com",
                icon = { Icon(painterResource(id = android.R.drawable.ic_menu_myplaces), contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Contrasena Field
            Text(text = "Ingresa tu contraseña", fontSize = 14.sp, color = Color.Black, modifier = Modifier.fillMaxWidth())
            CustomInputField(
                value = viewModel.contrasena,
                onValueChange = { viewModel.contrasena = it },
                placeholder = "********",
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Confirmar Contrasena Field
            Text(text = "Confirma tu contraseña", fontSize = 14.sp, color = Color.Black, modifier = Modifier.fillMaxWidth())
            CustomInputField(
                value = viewModel.confirmarContrasena,
                onValueChange = { viewModel.confirmarContrasena = it },
                placeholder = "********",
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.registrar(
                        onSuccess = {
                            Toast.makeText(context, "¡Usuario registrado con éxito!", Toast.LENGTH_SHORT).show()
                            onRegisterSuccess()
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VerdePrimario),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Continuar", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "¿Ya tienes una cuenta?",
                fontSize = 14.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNavigateToLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VerdePrimario),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VerdePrimario)
            ) {
                Text("Inicia sesión", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "𝄞",
            color = VerdePrimario,
            fontSize = 100.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}
