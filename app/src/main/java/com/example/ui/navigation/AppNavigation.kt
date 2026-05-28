package com.example.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.home.MainScreen
import com.example.ui.home.ViewerScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = navController,
            startDestination = "main"
        ) {
            composable("main") {
                MainScreen(navController = navController)
            }
            composable(
                "viewer?encodedUri={encodedUri}&isVideo={isVideo}",
                arguments = listOf(
                    navArgument("encodedUri") { type = NavType.StringType },
                    navArgument("isVideo") { type = NavType.BoolType }
                )
            ) { backStackEntry ->
                val uriString = backStackEntry.arguments?.getString("encodedUri")
                val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
                if (uriString != null) {
                    val decodedUri = Uri.parse(Uri.decode(uriString))
                    ViewerScreen(uri = decodedUri, isVideo = isVideo, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
