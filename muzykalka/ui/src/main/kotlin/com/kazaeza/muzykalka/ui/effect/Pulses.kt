package com.kazaeza.muzykalka.ui.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.kazaeza.muzykalka.music.Overtones
import com.kazaeza.muzykalka.music.PulseKind
import com.kazaeza.muzykalka.music.SoundPulse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** A pluck that is still sounding: what it is and how many seconds ago it was struck. */
data class LivePulse(val pulse: SoundPulse, val age: Float) {
    val strength: Float get() = pulse.strength
    val kind: PulseKind get() = pulse.kind
}

/**
 * O-35: the last few plucks and the clock they are measured by. Strings and the sphere read [live] while drawing,
 * so only the drawing is redone each frame; when nothing sounds, the clock stops and nothing is redrawn.
 */
@Stable
class PulseBoard internal constructor() {
    private class Slot(val pulse: SoundPulse, val born: Float)

    private val slots = mutableStateListOf<Slot>()
    private var clock by mutableFloatStateOf(0f)

    /** Something still rings. */
    val active: Boolean get() = slots.isNotEmpty()

    private val listeners = mutableListOf<(SoundPulse) -> Unit>()

    /**
     * O-38: [listener] hears every pluck struck from now on (the bodies of the sphere that are drawn by OpenGL take their
     * events this way, outside the composition). Returns what takes it away again. Main thread only.
     */
    fun addListener(listener: (SoundPulse) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /** Strike a pluck now. Only the last [MAX] are kept. */
    fun add(pulse: SoundPulse) {
        slots.add(Slot(pulse, clock))
        while (slots.size > MAX) slots.removeAt(0)
        listeners.toList().forEach { it(pulse) }
    }

    /** The plucks that still ring, oldest first, with their age in seconds. Read in a draw block. */
    fun live(): List<LivePulse> {
        val now = clock
        return slots.mapNotNull { s -> (now - s.born).takeIf { it in 0f..Overtones.LIFE }?.let { LivePulse(s.pulse, it) } }
    }

    internal fun advance(seconds: Float) {
        clock += seconds
        slots.removeAll { clock - it.born > Overtones.LIFE }
    }

    companion object {
        /** The strings and the sphere draw at most this many plucks at once. */
        const val MAX = 4

        /** A board that stands still: [aged] are plucks that were struck that many seconds ago (screenshots, tests). */
        fun frozen(vararg aged: Pair<Float, SoundPulse>): PulseBoard = PulseBoard().also { b ->
            b.clock = aged.maxOfOrNull { it.first } ?: 0f
            aged.takeLast(MAX).forEach { (age, p) -> b.slots.add(Slot(p, b.clock - age)) }
        }
    }
}

/**
 * A board fed from [flow] (plucks of the sound that plays), with the clock that ticks while something rings.
 * The plucks arrive in real time, so they are drawn the moment they come.
 */
@Composable
fun rememberPulses(flow: Flow<SoundPulse>?): PulseBoard {
    val board = remember { PulseBoard() }
    LaunchedEffect(flow) { flow?.collect { board.add(it) } }
    LaunchedEffect(board) {
        while (true) {
            snapshotFlow { board.active }.first { it }
            var last = -1L
            while (board.active) {
                withFrameNanos { now ->
                    if (last >= 0) board.advance(((now - last) / 1e9f).coerceAtMost(0.1f))
                    last = now
                }
            }
        }
    }
    return board
}

/** How hard the plucks that still ring push now, 0…[KICK_MAX]: the sum of what each is worth to the sphere as it dies away. */
internal fun List<LivePulse>.kick(): Float = sumOf { (it.orbStrength * Overtones.kick(it.age)).toDouble() }.toFloat().coerceAtMost(KICK_MAX)
