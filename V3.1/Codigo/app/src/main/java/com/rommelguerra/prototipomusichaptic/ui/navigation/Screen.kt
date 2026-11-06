package com.rommelguerra.prototipomusichaptic.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object MainMenu : Screen("main_menu")
    object Archive : Screen("archive")
    object Profile : Screen("profile")
    object Calibration : Screen("calibration")
    object Settings : Screen("settings")
    object Tutorial : Screen("tutorial")
    object Progress : Screen("progress")
}

