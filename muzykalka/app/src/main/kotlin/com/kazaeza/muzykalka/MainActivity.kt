package com.kazaeza.muzykalka

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kazaeza.muzykalka.crash.CrashReporter
import com.kazaeza.muzykalka.nav.MuzykalkaRoot
import com.kazaeza.muzykalka.ui.theme.MuzykalkaTheme

class MainActivity : ComponentActivity() {
    // O-36: while the app is on screen the sound output stays open for the taps
    override fun onStart() {
        super.onStart()
        (application as MuzykalkaApp).graph.setOnScreen(true)
    }

    override fun onStop() {
        (application as MuzykalkaApp).graph.setOnScreen(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as MuzykalkaApp).graph
        graph.seedBook() // O-42: the songbook is part of the library; in the background, once per piece
        val crash = CrashReporter.pending(this)
        setContent {
            MuzykalkaTheme {
                MuzykalkaRoot(graph)
                var report by remember { mutableStateOf(crash) }
                report?.let { text ->
                    AlertDialog(
                        onDismissRequest = {
                            CrashReporter.clear(this)
                            report = null
                        },
                        title = { Text("Приложение упало в прошлый раз") },
                        text = { Text("Отправь отчёт — по нему проще всего починить.") },
                        confirmButton = {
                            TextButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Музыкалка: отчёт о падении")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                startActivity(Intent.createChooser(send, "Отправить отчёт"))
                                CrashReporter.clear(this)
                                report = null
                            }) { Text("Отправить") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                CrashReporter.clear(this)
                                report = null
                            }) { Text("Не надо") }
                        },
                    )
                }
            }
        }
    }
}
