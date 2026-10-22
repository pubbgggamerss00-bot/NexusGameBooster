package com.nexus.gamebooster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexus.gamebooster.navigation.NgbApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // NgbApp collects persisted settings (language + theme) and applies both
        // around the whole tree, so a theme change takes effect immediately.
        setContent {
            NgbApp()
        }
    }
}
