package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.LiveMonitorAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.LiveMonitorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LiveMonitorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup modern full screen display bounds
        enableEdgeToEdge()
        
        // Auto-start background monitors
        viewModel.startService()

        setContent {
            MyApplicationTheme {
                LiveMonitorAppScreen(viewModel = viewModel)
            }
        }
    }
}
