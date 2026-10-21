package com.friendsvibe.autopilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.friendsvibe.autopilot.ui.FriendsVibeApp
import com.friendsvibe.autopilot.ui.FriendsVibeTheme
import com.friendsvibe.autopilot.ui.Ink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        )
        setContent {
            FriendsVibeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    FriendsVibeApp()
                }
            }
        }
    }
}
