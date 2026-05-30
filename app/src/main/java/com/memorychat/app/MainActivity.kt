package com.memorychat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.memorychat.app.ui.theme.MemoryChatTheme
import com.memorychat.app.ui.MainNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemoryChatTheme {
                MainNavHost()
            }
        }
    }
}
