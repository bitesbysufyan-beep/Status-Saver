package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      FirebaseApp.initializeApp(this)
    } catch(e: Exception) {
      e.printStackTrace()
    }
    try {
      val prefs = getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
      prefs.edit().putLong("last_app_open_time", System.currentTimeMillis()).apply()
    } catch(e: Exception) {
      e.printStackTrace()
    }
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AppNavigation()
      }
    }
  }
}

