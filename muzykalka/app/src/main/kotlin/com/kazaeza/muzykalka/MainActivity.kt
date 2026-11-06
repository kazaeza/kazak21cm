package com.kazaeza.muzykalka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kazaeza.muzykalka.nav.MuzykalkaRoot
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as MuzykalkaApp).graph
        setContent {
            MuzykalkaTheme {
                MuzykalkaRoot(graph)
            }
        }
    }
}
