package com.rommelguerra.prototipomusichaptic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rommelguerra.prototipomusichaptic.ui.navigation.Screen
import com.rommelguerra.prototipomusichaptic.ui.screens.*
import com.rommelguerra.prototipomusichaptic.ui.theme.MusicHapticTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicHapticTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPreferences = context.getSharedPreferences("SesionUsuario", android.content.Context.MODE_PRIVATE)
    val isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false)

    NavHost(
        navController = navController,
        startDestination = "experience"
    ) {
        composable("experience") {
            ExperienceScreen()
        }
        composable(Screen.Splash.route) {
            SplashScreen(onTimeout = {
                val nextRoute = if (isLoggedIn) Screen.MainMenu.route else Screen.Login.route
                navController.navigate(nextRoute) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.MainMenu.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }
        composable(Screen.MainMenu.route) {
            MainMenuScreen(onNavigate = { route ->
                navController.navigate(route)
            })
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Archive.route) {
            ArchiveScreen(
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable("file_mode/{songName}") { backStackEntry ->
            val songName = backStackEntry.arguments?.getString("songName")
            FileModeScreen(
                songName = songName,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Progress.route) {
            ProgressScreen(
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

