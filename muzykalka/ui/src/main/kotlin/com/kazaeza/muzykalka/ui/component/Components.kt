package com.kazaeza.muzykalka.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.ui.theme.DegreeColors

/** Minimum touch target in game screens: hands are busy with the guitar. */
val GameTouchTarget = 72.dp

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = GameTouchTarget),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(text, style = MaterialTheme.typography.headlineMedium)
    }
}

/**
 * The I–VII answer pad (Tir, «Что это было?», Улов). Shows [degrees] in the user's notation,
 * coloured by [DegreeColors]. Rows of up to 4 keys, each ≥ [GameTouchTarget].
 */
@Composable
fun DegreeKeypad(
    degrees: List<Degree>,
    notation: DegreeNotation,
    onDegree: (Degree) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        degrees.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d ->
                    Button(
                        onClick = { onDegree(d) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = GameTouchTarget),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DegreeColors.of(d), contentColor = Color(0xFF111111)),
                    ) {
                        Text(DegreeFormatter.degree(d, notation), style = MaterialTheme.typography.headlineMedium)
                    }
                }
                repeat(4 - row.size) { Column(Modifier.weight(1f)) {} }
            }
        }
    }
}

/** A titled card used by skeleton screens and summaries. */
@Composable
fun InfoCard(title: String, body: String, modifier: Modifier = Modifier, badge: String? = null) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (badge != null) {
                    Text(badge, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End)
                }
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
