package com.glassous.dsha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.glassous.dsha.ui.nav.DshaNavHost
import com.glassous.dsha.ui.theme.DshaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DshaTheme {
                DshaNavHost()
            }
        }
    }
}
