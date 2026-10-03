package com.example.networktoggle

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.network.NetworkModeManager
import com.example.networktoggle.theme.NetworkToggleTheme
import com.example.networktoggle.ui.main.MainScreen
import com.example.networktoggle.ui.main.MainScreenViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainScreenViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val networkModeManager = NetworkModeManager(applicationContext)
        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return MainScreenViewModel(networkModeManager) as T
                }
            }
        )[MainScreenViewModel::class.java]

        handleShortcutIntent(intent)

        setContent {
            NetworkToggleTheme {
                MainScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.getStringExtra("TARGET_MODE")) {
            "5G" -> viewModel.setMode(NetworkMode.FIVE_G)
            "4G" -> viewModel.setMode(NetworkMode.FOUR_G)
        }
    }
}
