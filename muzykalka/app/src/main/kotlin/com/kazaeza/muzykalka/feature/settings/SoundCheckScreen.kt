package com.kazaeza.muzykalka.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.LocalAppGraph
import com.kazaeza.muzykalka.data.settings.Settings
import com.kazaeza.muzykalka.feature.common.KeepScreenOn
import com.kazaeza.muzykalka.feature.common.MicPermissionGate
import com.kazaeza.muzykalka.feature.common.ScreenFrame
import com.kazaeza.muzykalka.game.HuntDriver
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.NoteName
import com.kazaeza.muzykalka.music.RuNames
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.ScoreNote
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.GradientText
import com.kazaeza.muzykalka.ui.component.MicPulse
import com.kazaeza.muzykalka.ui.component.TunerGauge
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.DegreeColors
import com.kazaeza.muzykalka.ui.theme.Juicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Ticket M-08: tuner + level meter + test sounds. */
@Composable
fun SoundCheckScreen(onBack: () -> Unit) {
    MicPermissionGate(onCancel = onBack) { SoundCheckContent(onBack) }
}

@Composable
private fun SoundCheckContent(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val settings by graph.settings.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    var level by remember { mutableDoubleStateOf(-90.0) }
    var note by remember { mutableStateOf<MidiNote?>(null) }
    var cents by remember { mutableStateOf<Double?>(null) }
    var hz by remember { mutableStateOf<Double?>(null) }
    val error by graph.pitchInput.error.collectAsState()
    var previewJob by remember { mutableStateOf<Job?>(null) }
    fun preview(score: Score) {
        val previous = previewJob
        previewJob = scope.launch {
            previous?.cancelAndJoin()
            graph.audioOutput.a4Hz = settings.a4Hz
            graph.audioOutput.play(score)
        }
    }
    KeepScreenOn()

    DisposableEffect(Unit) {
        onDispose {
            if (previewJob?.isActive == true) graph.audioOutput.stop()
            previewJob?.cancel()
        }
    }
    DisposableEffect(settings.inputDeviceId, settings.a4Hz, settings.micBoostDb) {
        graph.pitchInput.start(
            settings.inputDeviceId,
            settings.a4Hz,
            boostDb = settings.micBoostDb.toDouble(),
        )
        onDispose { graph.pitchInput.stop() }
    }
    LaunchedEffect(settings.a4Hz) {
        graph.pitchInput.frames.collect { f ->
            level = f.rmsDb
            val est = f.estimate
            if (est != null && est.clarity > 0.8 && f.rmsDb > -55) {
                val r = MidiNote.nearest(est.hz, settings.a4Hz)
                note = r.note
                cents = r.cents
                hz = est.hz
            } else if (f.rmsDb < -60) {
                cents = null
            }
        }
    }

    ScreenFrame(title = "Проверка звука", subtitle = "тюнер и тест микрофона", accent = Accents.Kamerton, onBack = onBack) {
        item(key = "tuner") {
            GlassCard(Modifier.fillMaxWidth(), accent = Accents.Kamerton) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val n = note
                    val inTune = cents?.let { abs(it) <= 5 } == true
                    GradientText(
                        n?.let { RuNames.note(HuntDriver.PAD[it.pitchClass.value], settings.noteNaming) + it.octave } ?: "—",
                        MaterialTheme.typography.displayLarge,
                        if (inTune) Accents.Good else Accents.Kamerton,
                    )
                    Text(
                        hz?.let { String.format(java.util.Locale.US, "%.1f Гц · %+.0f ц", it, cents ?: 0.0) } ?: "сыграй открытую струну",
                        style = MaterialTheme.typography.titleMedium,
                        color = Juicy.TextDim,
                    )
                    TunerGauge(cents)
                }
            }
        }
        item(key = "level") {
            val activeColor = note?.let { DegreeColors.ofSemitone(it.pitchClass.value) } ?: Juicy.Cyan
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MicPulse(level, null, color = activeColor)
                    Column(Modifier.weight(1f)) {
                        Text(String.format(java.util.Locale.US, "уровень %.0f дБ", level), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                error != null -> error ?: ""
                                level < -60 -> "тишина — поднеси телефон к гитаре"
                                level < -50 -> "ниже порога — подними усиление или подойди ближе"
                                level < -40 -> "тихо, но слышно"
                                else -> "отлично слышу"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (error != null) Juicy.Hot else Juicy.TextDim,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                MicLevelBar(levelDb = level, color = activeColor)
                Spacer(Modifier.height(14.dp))
                Text("Усиление микрофона: +${settings.micBoostDb} дБ", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = settings.micBoostDb.toFloat(),
                    onValueChange = { v ->
                        val db = v.roundToInt().coerceIn(0, 24)
                        scope.launch { graph.settings.update { it.copy(micBoostDb = db) } }
                    },
                    valueRange = 0f..24f,
                    steps = 7,
                )
                Text("если гитара далеко — подними усиление", style = MaterialTheme.typography.bodySmall, color = Juicy.TextDim)
            }
        }
        item(key = "test") {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Тест звука", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                val c = Key(NoteName.parse("C"), Mode.MAJOR)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Гитара", onClick = { preview(Score(90.0, listOf(ScoreNote(0.0, 2.0, Voice.GUITAR, MidiNote(52))))) }, modifier = Modifier.weight(1f), colors = Accents.Taper)
                        GhostButton("Пиано", onClick = { preview(Arranger().line(com.kazaeza.muzykalka.music.DegreeNote.parseLine("1 3 5 1'"), c, beatsEach = 0.5)) }, modifier = Modifier.weight(1f), colors = Accents.Kamerton)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Каденция", onClick = { preview(Arranger().chords(listOf("I", "IV", "V7", "I").map(ChordDegree::parse), c, tempoBpm = 120.0)) }, modifier = Modifier.weight(1f), colors = Accents.Tir)
                        GhostButton("Бит", onClick = {
                            val beat = (0 until 8).flatMap { i ->
                                listOfNotNull(
                                    ScoreNote(i * 0.5, 0.25, Voice.HAT, velocity = 0.6f),
                                    if (i % 4 == 0) ScoreNote(i * 0.5, 0.5, Voice.KICK) else null,
                                    if (i % 4 == 2) ScoreNote(i * 0.5, 0.5, Voice.SNARE) else null,
                                )
                            }
                            preview(Score(100.0, beat))
                        }, modifier = Modifier.weight(1f), colors = Accents.Recorder)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Пока звучит приложение, микрофон в играх не слушает — чтобы не принять звук динамика за твою ноту.", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
            }
        }
    }
}

private const val METER_MIN_DB = -80.0
private const val METER_MAX_DB = 0.0
private const val GATE_THRESHOLD_DB = -50.0

@Composable
private fun MicLevelBar(levelDb: Double, color: Color, modifier: Modifier = Modifier) {
    val span = METER_MAX_DB - METER_MIN_DB
    val levelFrac = ((levelDb - METER_MIN_DB) / span).coerceIn(0.0, 1.0).toFloat()
    val gateFrac = ((GATE_THRESHOLD_DB - METER_MIN_DB) / span).toFloat()
    val barColor = if (levelDb >= GATE_THRESHOLD_DB) Juicy.Good else color
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val barH = 8.dp.toPx()
            val top = (size.height - barH) / 2f
            val r = CornerRadius(barH / 2f, barH / 2f)
            drawRoundRect(Juicy.Ink4, Offset(0f, top), Size(size.width, barH), r)
            if (levelFrac > 0.005f) {
                drawRoundRect(barColor, Offset(0f, top), Size(size.width * levelFrac, barH), r)
            }
            val gateX = size.width * gateFrac
            drawLine(
                color = Juicy.Gold,
                start = Offset(gateX, 1.dp.toPx()),
                end = Offset(gateX, size.height - 1.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("−80 дБ", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
            Text("порог −50 дБ", style = MaterialTheme.typography.bodySmall, color = Juicy.Gold)
            Text("0 дБ", style = MaterialTheme.typography.bodySmall, color = Juicy.TextFaint)
        }
    }
}
