package com.kazaeza.muzykalka.feature.common

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kazaeza.muzykalka.game.ModeId
import com.kazaeza.muzykalka.game.Variant
import com.kazaeza.muzykalka.ui.component.GhostButton
import com.kazaeza.muzykalka.ui.component.GlassCard
import com.kazaeza.muzykalka.ui.component.JuicyButton
import com.kazaeza.muzykalka.ui.effect.AuroraBackground
import com.kazaeza.muzykalka.ui.theme.Accents
import com.kazaeza.muzykalka.ui.theme.Juicy
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Look of each mode: emoji + gradient. */
object ModeVisuals {
    fun emoji(mode: ModeId): String = when (mode) {
        ModeId.HUNT -> "🏹"
        ModeId.TIR -> "🎯"
        ModeId.KAMERTON -> "🎤"
        ModeId.PARROT -> "🦜"
        ModeId.TAPER -> "🎹"
        ModeId.RECORDER -> "📼"
    }

    fun accent(mode: ModeId): List<Color> = when (mode) {
        ModeId.HUNT -> Accents.Hunt
        ModeId.TIR -> Accents.Tir
        ModeId.KAMERTON -> Accents.Kamerton
        ModeId.PARROT -> Accents.Parrot
        ModeId.TAPER -> Accents.Taper
        ModeId.RECORDER -> Accents.Recorder
    }

    fun accent(variant: Variant): List<Color> = accent(variant.mode)
}

/** Versions shipped in this build; variants from later versions are shown as «скоро». */
const val APP_STAGE = "0.1"

fun Variant.isAvailable(): Boolean = since <= APP_STAGE
fun ModeId.isAvailable(): Boolean = since <= APP_STAGE

private val RU = Locale.forLanguageTag("ru")

fun ruDate(date: LocalDate): String {
    val dow = date.dayOfWeek.getDisplayName(TextStyle.FULL, RU).replaceFirstChar { it.uppercase() }
    val month = date.month.getDisplayName(TextStyle.FULL, RU)
    return "$dow, ${date.dayOfMonth} $month"
}

/** "через 3 дн.", "сегодня", "завтра", "просрочено на 2 дн." */
fun ruDue(dueDay: Int?, today: Int): String = when {
    dueDay == null -> "ждёт первого заказа"
    dueDay < today -> "просрочено на ${today - dueDay} дн."
    dueDay == today -> "сегодня"
    dueDay == today + 1 -> "завтра"
    else -> "через ${dueDay - today} дн."
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

/**
 * Standard screen: aurora background, big title, optional back arrow and actions, scrolling
 * column with 16 dp gutters.
 */
@Composable
fun ScreenFrame(
    title: String,
    accent: List<Color> = Accents.Calm,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    AuroraBackground(accent, Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "__header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) { Text("←", style = MaterialTheme.typography.headlineMedium, color = Juicy.Text) }
                        Spacer(Modifier.size(6.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineLarge, color = Juicy.Text)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Juicy.TextDim)
                    }
                    actions()
                }
            }
            content()
        }
    }
}

/** Round icon button used in headers ("⚙️", "✕"). */
@Composable
fun IconBubble(icon: String, onClick: () -> Unit, modifier: Modifier = Modifier, description: String? = null) {
    var m = modifier
        .size(46.dp)
        .clip(CircleShape)
        .clickable(onClick = onClick)
    if (description != null) m = m.semantics { contentDescription = description }
    Box(m, contentAlignment = Alignment.Center) {
        Text(icon, style = MaterialTheme.typography.headlineSmall)
    }
}

/** Keeps the screen on while composed (games: hands are on the guitar). */
@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

fun hasMicPermission(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Shows [content] once RECORD_AUDIO is granted; otherwise a friendly explanation with a button
 * (and a path to system settings after "don't ask again").
 */
@Composable
fun MicPermissionGate(onCancel: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasMicPermission(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        askedOnce = true
    }
    if (granted) {
        content()
        return
    }
    AuroraBackground(Accents.Hunt, Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎙️", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(16.dp))
            GlassCard(accent = Accents.Hunt) {
                Text("Мне нужно тебя слышать", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Микрофон определяет, какую ноту ты сыграл. Звук никуда не записывается и не отправляется — анализ идёт прямо в телефоне.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Juicy.TextDim,
                )
            }
            Spacer(Modifier.height(20.dp))
            JuicyButton("Разрешить микрофон", onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.fillMaxWidth(), colors = Accents.Hunt, leading = "🎸")
            if (askedOnce) {
                Spacer(Modifier.height(12.dp))
                GhostButton("Открыть настройки приложения", onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            Text("Назад", style = MaterialTheme.typography.labelLarge, color = Juicy.TextDim, textAlign = TextAlign.Center, modifier = Modifier.clickable(onClick = onCancel).padding(12.dp))
        }
    }
}
